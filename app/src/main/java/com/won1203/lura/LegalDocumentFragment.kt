package com.won1203.lura

import android.os.Bundle
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.text.util.LinkifyCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.won1203.lura.databinding.FragmentLegalDocumentBinding

class LegalDocumentFragment : Fragment() {

    private var _binding: FragmentLegalDocumentBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLegalDocumentBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.legalDocumentBackButton.setOnClickListener {
            findNavController().navigateUp()
        }

        val document = documentFor(
            arguments?.getString(ARG_DOCUMENT_TYPE)
        )
        binding.legalDocumentTitle.setText(document.titleRes)
        binding.legalDocumentBody.setText(document.bodyRes)
        LinkifyCompat.addLinks(
            binding.legalDocumentBody,
            Linkify.WEB_URLS or Linkify.EMAIL_ADDRESSES
        )
        binding.legalDocumentBody.movementMethod = LinkMovementMethod.getInstance()
    }

    private fun documentFor(documentType: String?): LegalDocument =
        when (documentType) {
            DOCUMENT_OPEN_SOURCE -> LegalDocument(
                titleRes = R.string.app_info_open_source_title,
                bodyRes = R.string.app_info_open_source_body
            )
            DOCUMENT_AUDIO -> LegalDocument(
                titleRes = R.string.app_info_audio_license_title,
                bodyRes = R.string.app_info_audio_license_body
            )
            else -> LegalDocument(
                titleRes = R.string.app_info_privacy_title,
                bodyRes = R.string.app_info_privacy_body
            )
        }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private data class LegalDocument(
        val titleRes: Int,
        val bodyRes: Int
    )

    companion object {
        const val ARG_DOCUMENT_TYPE = "document_type"
        const val DOCUMENT_PRIVACY = "privacy"
        const val DOCUMENT_OPEN_SOURCE = "open_source"
        const val DOCUMENT_AUDIO = "audio"
    }
}
