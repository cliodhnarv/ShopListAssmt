package com.mmgpt.shoplistgpt

import android.content.Context
import android.view.View
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.RecyclerView
import androidx.test.core.app.ActivityScenario
import androidx.test.platform.app.InstrumentationRegistry
import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Behavioural checks shared by the original and refactored category pages. */
@RunWith(Parameterized::class)
class CategoryPageBehaviourTest(private val plan: Boolean) {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val prefs get() = instrumentation.targetContext
        .getSharedPreferences("state", Context.MODE_PRIVATE)
    private var savedState: String? = null
    private var scenario: ActivityScenario<MainActivity>? = null
    private lateinit var activity: MainActivity
    private lateinit var model: ShoppingListViewModel
    private lateinit var fragment: Fragment
    private val category = ShoppingCategory(42, "Selected category", listOf(
        ShoppingItem(101, "Milk"), ShoppingItem(202, "Bread", isChecked = true)
    ))
    private val fixture get() = listOf(ShoppingCategory(7, "Other category", emptyList()), category)
    private val root get() = fragment.requireView()
    private val adapter get() = root.findViewById<RecyclerView>(R.id.recyclerView).adapter as ItemAdapter

    @Before fun launch() {
        savedState = prefs.getString("state", null)
        scenario = ActivityScenario.launch(MainActivity::class.java)
        scenario!!.onActivity {
            activity = it
            model = ViewModelProvider(it)[ShoppingListViewModel::class.java]
            model.setCategories(fixture)
            // Use the real factories with a nonzero index; keep pager mechanics in Task 2 tests.
            fragment = if (plan) PlanCategoryFragment.newInstance(1) else CategoryFragment.newInstance(1)
            it.supportFragmentManager.beginTransaction()
                .add(android.R.id.content, fragment, "task3-page").commitNow()
        }
        awaitItems(category.items)
    }

    @After fun restoreState() {
        scenario?.close()
        val editor = prefs.edit()
        if (savedState == null) editor.remove("state") else editor.putString("state", savedState)
        check(editor.commit())
    }

    @Test fun rendersModeAndCategoryUpdates() {
        main {
            assertEquals(category.heading, root.findViewById<TextView>(R.id.tvHeading).text.toString())
            assertEquals(if (plan) "" else "1/2", root.findViewById<TextView>(R.id.tvCount).text.toString())
            assertEquals(if (plan) View.VISIBLE else View.GONE,
                root.findViewById<View>(R.id.chkSelectAll).visibility)
            assertEquals(if (plan) listOf(-1L, 101L, 202L, -2L) else listOf(101L, 202L),
                adapter.currentList.map { it.id })
            if (plan) {
                assertTrue(adapter.currentList.first().isPlaceholder)
                assertTrue(adapter.currentList.last().isPlaceholder)
            }
            model.setCategories(listOf(fixture.first(), category.copy(heading = "Updated",
                items = listOf(category.items.first().copy(isChecked = true)))))
        }
        awaitItems(listOf(category.items.first().copy(isChecked = true)))
        main {
            assertEquals("Updated", root.findViewById<TextView>(R.id.tvHeading).text.toString())
            assertEquals(if (plan) "" else "1/1", root.findViewById<TextView>(R.id.tvCount).text.toString())
            if (plan) assertTrue(root.findViewById<CheckBox>(R.id.chkSelectAll).isChecked)
        }
    }

    @Test fun checkAndModeSpecificActionsUpdateModel() {
        if (plan) {
            main { root.findViewById<CheckBox>(R.id.chkSelectAll).performClick() }
            awaitItems(category.items.map { it.copy(isChecked = true) })
        } else {
            row(0) { it.findViewById<View>(R.id.btnFlag).performClick() }
            awaitItems(listOf(category.items.first().copy(isFlagged = true), category.items.last()))
        }
        row(if (plan) 1 else 0) { row ->
            assertEquals(if (plan) View.GONE else View.VISIBLE, row.findViewById<View>(R.id.btnFlag).visibility)
            assertEquals(if (plan) View.VISIBLE else View.GONE, row.findViewById<View>(R.id.btnEdit).visibility)
            assertEquals(if (plan) View.VISIBLE else View.GONE, row.findViewById<View>(R.id.btnDelete).visibility)
            row.findViewById<CheckBox>(R.id.chkPurchased).performClick()
        }
        awaitItems(listOf(category.items.first().copy(isChecked = !plan, isFlagged = !plan), category.items.last()))
        main {
            assertEquals(fixture.first(), model.categories.value!![0])
            assertEquals(!plan, model.categories.value!![1].items.first().isChecked)
            if (plan) assertFalse(root.findViewById<CheckBox>(R.id.chkSelectAll).isChecked)
            else assertEquals("2/2", root.findViewById<TextView>(R.id.tvCount).text.toString())
        }
    }

    @Test fun recreatingViewUsesLatestCategoryAndContinuesObserving() {
        lateinit var previousRoot: View
        val changed = category.copy(heading = "Changed while detached", items = listOf(ShoppingItem(303, "Eggs")))
        main {
            previousRoot = root
            activity.supportFragmentManager.beginTransaction().detach(fragment).commitNow()
            assertNull(fragment.view)
            model.setCategories(listOf(fixture.first(), changed))
            activity.supportFragmentManager.beginTransaction().attach(fragment).commitNow()
            assertNotSame(previousRoot, root)
        }
        awaitItems(changed.items)
        main {
            assertEquals(changed.heading, root.findViewById<TextView>(R.id.tvHeading).text.toString())
            model.setCategories(fixture)
        }
        awaitItems(category.items)
        main { assertEquals(category.heading, root.findViewById<TextView>(R.id.tvHeading).text.toString()) }
    }

    @Test fun missingIndexKeepsExistingDisplayUntilCategoryReturns() {
        main {
            model.setCategories(emptyList())
            assertEquals(category.heading, root.findViewById<TextView>(R.id.tvHeading).text.toString())
            model.setCategories(listOf(fixture.first(), category.copy(heading = "Returned")))
            assertEquals("Returned", root.findViewById<TextView>(R.id.tvHeading).text.toString())
        }
        awaitItems(category.items)
    }

    private fun main(block: () -> Unit) = instrumentation.runOnMainSync(block)

    private fun awaitItems(expected: List<ShoppingItem>) = await {
        adapter.currentList.filterNot { it.isPlaceholder } == expected
    }

    private fun row(position: Int, action: (View) -> Unit) {
        await {
            root.findViewById<RecyclerView>(R.id.recyclerView)
                .findViewHolderForAdapterPosition(position) != null
        }
        main { action(root.findViewById<RecyclerView>(R.id.recyclerView)
            .findViewHolderForAdapterPosition(position)!!.itemView) }
    }

    private fun await(predicate: () -> Boolean) {
        val deadline = android.os.SystemClock.uptimeMillis() + 8000
        while (android.os.SystemClock.uptimeMillis() < deadline) {
            var ready = false
            main { ready = predicate() }
            if (ready) return
            android.os.SystemClock.sleep(25)
        }
        throw AssertionError("Timed out waiting for category page (plan=$plan)")
    }

    companion object {
        @JvmStatic @Parameterized.Parameters(name = "{index}-{0}")
        fun modes(): List<Array<Any>> = listOf(arrayOf<Any>(false), arrayOf<Any>(true))
    }
}
