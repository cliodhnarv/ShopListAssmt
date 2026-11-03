package com.mmgpt.shoplistgpt

import android.content.Context
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.doubleClick
import androidx.test.espresso.action.ViewActions.scrollTo
import androidx.test.espresso.action.ViewActions.swipeLeft
import androidx.test.espresso.action.ViewActions.swipeRight
import androidx.test.espresso.matcher.ViewMatchers.isDescendantOfA
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.platform.app.InstrumentationRegistry
import androidx.viewpager2.widget.ViewPager2
import com.mmgpt.shoplistgpt.adapter.CategorySummaryAdapter
import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.sameInstance
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Exercises touch handlers, pager and destination fragments together. */
@RunWith(Parameterized::class)
class CategoryNavigationIdentityTest(
    private val plan: Boolean,
    private val selection: String,
    private val condition: String
) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs get() = instrumentation.targetContext
        .getSharedPreferences("state", Context.MODE_PRIVATE)
    private var savedState: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var activity: MainActivity
    private lateinit var model: ShoppingListViewModel
    private lateinit var pager: ViewPager2
    private lateinit var fixture: List<ShoppingCategory>
    private val pageTrace = mutableListOf<Int>()

    @Before fun launch() {
        savedState = prefs.getString("state", null)
        // Deliberately non-sequential IDs; each category has a unique item identity.
        val count = when (condition) {
            "scrolled" -> 11
            "after-swipe" -> 4
            else -> 3
        }
        fixture = (0 until count).map { i ->
            ShoppingCategory(101L + i * 37, "Category $i",
                listOf(ShoppingItem(10001L + i * 53, "Identity marker $i")))
        }
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity {
            activity = it
            model = ViewModelProvider(it)[ShoppingListViewModel::class.java]
            model.setCategories(fixture)
            it.findViewById<View>(if (plan) R.id.btnPlan else R.id.btnShop).performClick()
            pager = it.findViewById(R.id.viewPager)
            pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) { pageTrace.add(position) }
            })
        }
        if (condition == "reordered") {
            // Visit pages before reordering so cached fragments must update too.
            fixture.indices.forEach { index ->
                main { pager.setCurrentItem(index + if (plan) 1 else 0, false) }
                await("warm category $index") { destination() != null }
            }
            main { model.reorderCategory(0, fixture.lastIndex) }
        }
        main { pager.setCurrentItem(if (plan) 0 else fixture.size, false) }
        await("overview and committed category order") {
            val overview = overview() ?: return@await false
            val adapter = overview.requireView().findViewById<RecyclerView>(R.id.recyclerView).adapter
            val ids = when (adapter) {
                is ItemAdapter -> adapter.currentList.filter { it.isHeading }.map { it.id }
                is CategorySummaryAdapter -> adapter.currentList.map { it.id }
                else -> emptyList()
            }
            ids == model.categories.value!!.map { it.id }
        }
    }

    @After fun restoreState() {
        scenario?.close()
        // Closing invokes onStop/saveState; restore the user's data afterwards.
        val editor = prefs.edit()
        if (savedState == null) editor.remove("state") else editor.putString("state", savedState)
        check(editor.commit())
    }

    @Test fun doubleTapOpensSelectedCategoryIdentity() {
        if (condition.startsWith("wrap-")) {
            verifyWraparound()
            return
        }
        if (condition == "after-swipe") {
            main { pager.setCurrentItem(0, false) }
            await("first category before wrapping to summary") { destination() != null }
            onView(withId(R.id.viewPager)).perform(swipeRight())
            await("summary reached by swipe") {
                pager.scrollState == ViewPager2.SCROLL_STATE_IDLE && overview() != null
            }
        }
        lateinit var selected: ShoppingCategory
        lateinit var overviewRoot: View
        main {
            val categories = model.categories.value!!
            val index = when (selection) {
                "first" -> 0
                "middle" -> categories.size / 2
                else -> categories.lastIndex
            }
            selected = categories[index]
            overviewRoot = overview()!!.requireView()
            val adapter = overviewRoot.findViewById<RecyclerView>(R.id.recyclerView).adapter!!
            val row = index + if (plan) 1 else 0
            // Capture actual row identity, rather than assuming offset arithmetic is correct.
            assertEquals("Selected row ID", selected.id, adapter.getItemId(row))
        }
        onView(allOf(withId(R.id.tvName), withText(selected.heading),
            isDescendantOfA(sameInstance(overviewRoot))))
            .perform(scrollTo(), doubleClick())

        await("double-tap navigation completes") {
            pager.scrollState == ViewPager2.SCROLL_STATE_IDLE && destination() != null
        }
        await("destination item list committed") {
            val adapter = destination()!!.requireView()
                .findViewById<RecyclerView>(R.id.recyclerView).adapter as ItemAdapter
            adapter.currentList.any { !it.isPlaceholder }
        }
        main {
            val fragment = destination()!!
            val index = fragment.requireArguments().getInt("index")
            val actual = model.categories.value!![index]
            val root = fragment.requireView()
            val rendered = (root.findViewById<RecyclerView>(R.id.recyclerView).adapter as ItemAdapter)
                .currentList.filterNot { it.isPlaceholder }
            assertEquals("Destination category ID for tapped ${selected.heading}", selected.id, actual.id)
            assertEquals("Rendered heading", selected.heading,
                root.findViewById<TextView>(R.id.tvHeading).text.toString())
            assertEquals("Rendered item identities", selected.items.map { it.id }, rendered.map { it.id })
            assertTrue("Destination mode", if (plan) fragment is PlanCategoryFragment else fragment is CategoryFragment)
        }
    }

    private fun verifyWraparound() {
        val right = condition == "wrap-right"
        val start = if (right) 0 else fixture.size
        val end = if (right) fixture.size else 0
        main { pager.setCurrentItem(start, false) }
        await("starting edge page") {
            pager.currentItem == start && pager.scrollState == ViewPager2.SCROLL_STATE_IDLE &&
                activity.supportFragmentManager.fragments.any { it.isResumed && it.view?.isShown == true }
        }
        onView(withId(R.id.viewPager)).perform(if (right) swipeRight() else swipeLeft())
        await("wraparound to opposite edge") {
            pager.currentItem == end && pager.scrollState == ViewPager2.SCROLL_STATE_IDLE &&
                (if (plan == right) destination() != null else overview() != null)
        }
        main {
            if (plan == right) {
                val expected = if (plan) fixture.last() else fixture.first()
                val fragment = destination()!!
                val actual = model.categories.value!![fragment.requireArguments().getInt("index")]
                assertEquals("Wrapped destination category ID", expected.id, actual.id)
                assertEquals(expected.heading, fragment.requireView()
                    .findViewById<TextView>(R.id.tvHeading).text.toString())
            } else {
                val adapter = overview()!!.requireView()
                    .findViewById<RecyclerView>(R.id.recyclerView).adapter!!
                assertEquals("Overview first category ID", fixture.first().id,
                    adapter.getItemId(if (plan) 1 else 0))
            }
        }
    }

    private fun overview(): Fragment? = activity.supportFragmentManager.fragments.firstOrNull {
        it.isResumed && it.view?.isShown == true &&
            (if (plan) it is PlanCategoriesFragment else it is ShopCategoriesFragment)
    }

    private fun destination(): Fragment? = activity.supportFragmentManager.fragments.firstOrNull {
        it.isResumed && it.view?.isShown == true &&
            (if (plan) it is PlanCategoryFragment else it is CategoryFragment)
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun await(description: String, predicate: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 8000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = predicate() }
            if (ready) return
            android.os.SystemClock.sleep(25)
        }
        var detail = ""
        main {
            detail = "page=${pager.currentItem}, selections=$pageTrace, fragments=" +
                activity.supportFragmentManager.fragments.joinToString { fragment ->
                    "${fragment.javaClass.simpleName}(index=${fragment.arguments?.getInt("index")}, " +
                        "resumed=${fragment.isResumed}, shown=${fragment.view?.isShown})"
                }
        }
        throw AssertionError("Timed out waiting for $description ($plan, $selection, $condition): $detail")
    }

    companion object {
        // Gradle includes this name in log filenames; keep Windows paths short.
        @JvmStatic @Parameterized.Parameters(name = "{index}-{0}-{1}-{2}")
        fun cases(): List<Array<Any>> = listOf(false, true).flatMap { plan ->
            listOf("first", "middle", "last").flatMap { selection ->
                listOf("stable", "reordered", "scrolled").map { condition ->
                    arrayOf<Any>(plan, selection, condition)
                }
            }
        } + listOf(arrayOf<Any>(false, "first", "after-swipe")) +
            listOf(false, true).flatMap { plan ->
                listOf("wrap-right", "wrap-left").map { condition ->
                    arrayOf<Any>(plan, "edge", condition)
                }
            }
    }
}
