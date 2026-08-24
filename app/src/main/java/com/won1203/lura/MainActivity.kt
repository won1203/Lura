package com.won1203.lura

import android.Manifest
import android.app.NotificationManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import com.won1203.lura.alarm.AlarmAppVisibility
import com.won1203.lura.alarm.AlarmRescheduler
import com.won1203.lura.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var fullScreenIntentDialogShown = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        setupBottomNavigation(navController)
        AlarmRescheduler.restoreEnabledAlarms(
            context = applicationContext,
            restorePlaybackInActiveWindow = false
        )
        if (!requestNotificationPermissionIfNeeded()) {
            checkFullScreenIntentPermission()
        }
    }

    override fun onStart() {
        super.onStart()
        AlarmAppVisibility.onActivityStarted()
    }

    override fun onStop() {
        AlarmAppVisibility.onActivityStopped()
        super.onStop()
    }

    private fun setupBottomNavigation(navController: NavController) {
        val bottomNavigation = findViewById<View>(R.id.bottom_navigation)
        BOTTOM_TAB_IDS.forEach { itemId ->
            bottomNavigation.findViewById<View>(itemId).setOnClickListener {
                navigateBottomTab(navController, itemId)
            }
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNavigation.visibility =
                if (destination.id == R.id.appInfoFragment ||
                    destination.id == R.id.legalDocumentFragment
                ) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            BOTTOM_TAB_IDS.forEach { itemId ->
                bottomNavigation.findViewById<View>(itemId).isSelected = destination.id == itemId
            }
        }
    }

    private fun navigateBottomTab(navController: NavController, itemId: Int): Boolean {
        if (navController.currentDestination?.id == itemId) {
            return true
        }

        return when (itemId) {
            R.id.homeFragment -> {
                navigateToHomeTab(navController)
                true
            }
            R.id.alarmSetupFragment,
            R.id.alarmHistoryFragment,
            R.id.reportFragment -> {
                navController.navigate(itemId, null, bottomTabNavOptions())
                true
            }
            else -> false
        }
    }

    private fun navigateToHomeTab(navController: NavController) {
        val didPopToHome = navController.popBackStack(R.id.homeFragment, false)
        if (!didPopToHome) {
            navController.navigate(R.id.homeFragment, null, bottomTabNavOptions())
        }
    }

    private fun bottomTabNavOptions(): NavOptions =
        NavOptions.Builder()
            // Bottom tabs remain top-level destinations with a predictable back stack.
            .setLaunchSingleTop(true)
            .setPopUpTo(R.id.homeFragment, false)
            .build()

    private fun requestNotificationPermissionIfNeeded(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false

        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            return false
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            POST_NOTIFICATIONS_REQUEST_CODE
        )
        return true
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode != POST_NOTIFICATIONS_REQUEST_CODE) return

        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            checkFullScreenIntentPermission()
        } else {
            showNotificationPermissionDialog()
        }
    }

    private fun showNotificationPermissionDialog() {
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.notification_permission_required_title)
            .setMessage(R.string.notification_permission_required_message)
            .setNegativeButton(R.string.permission_dialog_later) { _, _ ->
                checkFullScreenIntentPermission()
            }
            .setPositiveButton(R.string.permission_dialog_open_settings) { _, _ ->
                startActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            }
            .show()
    }

    private fun checkFullScreenIntentPermission() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.UPSIDE_DOWN_CAKE) return
        if (fullScreenIntentDialogShown) return

        val notificationManager = getSystemService(NotificationManager::class.java)
        if (notificationManager.canUseFullScreenIntent()) return

        fullScreenIntentDialogShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.full_screen_alarm_permission_title)
            .setMessage(R.string.full_screen_alarm_permission_message)
            .setNegativeButton(R.string.permission_dialog_later, null)
            .setPositiveButton(R.string.permission_dialog_open_settings) { _, _ ->
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                )
            }
            .show()
    }

    companion object {
        private val BOTTOM_TAB_IDS = intArrayOf(
            R.id.homeFragment,
            R.id.alarmSetupFragment,
            R.id.alarmHistoryFragment,
            R.id.reportFragment
        )
        private const val POST_NOTIFICATIONS_REQUEST_CODE = 4101
    }
}
