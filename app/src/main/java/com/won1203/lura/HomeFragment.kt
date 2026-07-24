package com.won1203.lura

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.won1203.lura.data.SoundCategory
import com.won1203.lura.data.SoundRepositoryProvider
import com.won1203.lura.databinding.FragmentHomeBinding
import kotlinx.coroutines.launch

class HomeFragment : Fragment() {

    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private val soundRepository = SoundRepositoryProvider.get()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.appInfoButton.setOnClickListener {
            findNavController().navigate(R.id.action_homeFragment_to_appInfoFragment)
        }
        loadCategories()
    }

    private fun loadCategories() {
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                soundRepository.getCategories()
            }.onSuccess(::renderCategories)
                .onFailure {
                    binding.categoryList.removeAllViews()
                }
        }
    }

    private fun renderCategories(categories: List<SoundCategory>) {
        binding.categoryList.removeAllViews()
        categories.forEach { category ->
            val itemView = layoutInflater.inflate(
                R.layout.item_sound_category,
                binding.categoryList,
                false
            )

            itemView.findViewById<ImageView>(R.id.category_background_image)
                .setImageResource(SoundCategoryArtwork.backgroundFor(category.id))
            itemView.findViewById<TextView>(R.id.category_name).text = category.name
            itemView.findViewById<TextView>(R.id.category_description).text = category.description
            itemView.findViewById<TextView>(R.id.category_mood).text = category.mood
            itemView.setOnClickListener {
                findNavController().navigate(
                    R.id.action_homeFragment_to_alarmSetupFragment,
                    bundleOf(AlarmSetupFragment.ARG_CATEGORY_ID to category.id)
                )
            }
            binding.categoryList.addView(itemView)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
