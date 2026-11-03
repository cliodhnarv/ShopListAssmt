package com.mmgpt.shoplistgpt

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.mmgpt.shoplistgpt.adapter.ItemAdapter
import com.mmgpt.shoplistgpt.databinding.FragmentCategoryBinding

/** Shared view lifetime and category observation for the Plan and Shop item pages. */
abstract class CategoryPageFragment : Fragment() {
    protected val viewModel: ShoppingListViewModel by activityViewModels()
    protected var index: Int = 0
        private set
    private var _binding: FragmentCategoryBinding? = null
    protected val binding get() = _binding!!

    final override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        index = arguments?.getInt(ARG_INDEX) ?: 0
    }

    final override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCategoryBinding.inflate(inflater, container, false)
        return binding.root
    }

    final override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        val adapter = setupItems()
        viewModel.categories.observe(viewLifecycleOwner) { list ->
            val category = list.getOrNull(index)
            if (category != null) {
                binding.tvHeading.text = category.heading
                renderCategory(category, adapter)
            }
        }
    }

    /** Configure the mode's adapter and actions before observation starts. */
    protected abstract fun setupItems(): ItemAdapter

    /** Render only the controls and item rows that differ between modes. */
    protected abstract fun renderCategory(category: ShoppingCategory, adapter: ItemAdapter)

    final override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    protected companion object {
        const val ARG_INDEX = "index"
    }
}
