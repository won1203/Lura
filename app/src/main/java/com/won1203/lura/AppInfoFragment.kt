package com.won1203.lura

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.pm.PackageInfoCompat
import androidx.core.os.bundleOf
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.won1203.lura.databinding.FragmentAppInfoBinding

class AppInfoFragment : Fragment() {

    private var _binding: FragmentAppInfoBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentAppInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.appInfoBackButton.setOnClickListener {
            findNavController().navigateUp()
        }

        bindAppIdentity()
        bindDocumentLinks()
    }

    private fun bindAppIdentity() {
        val context = requireContext()
        val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
        val versionName = packageInfo.versionName.orEmpty().ifBlank { "-" }
        val versionCode = PackageInfoCompat.getLongVersionCode(packageInfo)

        binding.appVersionText.text = getString(
            R.string.app_info_version_format,
            versionName,
            versionCode
        )
        binding.packageNameText.text = context.packageName
    }

    private fun bindDocumentLinks() {
        binding.privacyPolicyContent.appInfoLinkTitle.setText(
            R.string.app_info_privacy_title
        )
        binding.privacyPolicyContent.appInfoLinkSubtitle.setText(
            R.string.app_info_privacy_subtitle
        )
        binding.privacyPolicyCard.setOnClickListener {
            openDocument(LegalDocumentFragment.DOCUMENT_PRIVACY)
        }

        binding.openSourceLicenseContent.appInfoLinkTitle.setText(
            R.string.app_info_open_source_title
        )
        binding.openSourceLicenseContent.appInfoLinkSubtitle.setText(
            R.string.app_info_open_source_subtitle
        )
        binding.openSourceLicenseCard.setOnClickListener {
            openDocument(LegalDocumentFragment.DOCUMENT_OPEN_SOURCE)
        }

        binding.audioLicenseContent.appInfoLinkTitle.setText(
            R.string.app_info_audio_license_title
        )
        binding.audioLicenseContent.appInfoLinkSubtitle.setText(
            R.string.app_info_audio_license_subtitle
        )
        binding.audioLicenseCard.setOnClickListener {
            openDocument(LegalDocumentFragment.DOCUMENT_AUDIO)
        }
    }

    private fun openDocument(documentType: String) {
        findNavController().navigate(
            R.id.action_appInfoFragment_to_legalDocumentFragment,
            bundleOf(LegalDocumentFragment.ARG_DOCUMENT_TYPE to documentType)
        )
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
