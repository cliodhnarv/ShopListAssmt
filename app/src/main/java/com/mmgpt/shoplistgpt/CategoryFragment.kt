package com.mmgpt.shoplistgpt

import android.os.Bundle
import com.mmgpt.shoplistgpt.adapter.ItemAdapter

class CategoryFragment : CategoryPageFragment() {

    override fun setupItems(): ItemAdapter {
        val adapter = ItemAdapter(onAction = { item, action ->
            when (action) {
                ItemAdapter.Action.CHECK -> viewModel.checkItem(item)
                ItemAdapter.Action.FLAG -> viewModel.flagItem(item)
                // EDIT and DELETE actions are not applicable in this fragment
                ItemAdapter.Action.EDIT, ItemAdapter.Action.DELETE -> {}
            }
        })
        binding.recyclerView.adapter = adapter
        return adapter
    }

    override fun renderCategory(category: ShoppingCategory, adapter: ItemAdapter) {
        binding.tvCount.text = "${category.remainingCount}/${category.totalCount}"
        adapter.submitList(category.items)
    }

    companion object {
        fun newInstance(index: Int): CategoryFragment {
            val fragment = CategoryFragment()
            fragment.arguments = Bundle().apply { putInt(ARG_INDEX, index) }
            return fragment
        }
    }
}
