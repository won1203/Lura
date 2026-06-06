package com.example.lura

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import com.example.lura.alarm.AlarmAppVisibility
import com.example.lura.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        setupBottomNavigation(navController)
        requestNotificationPermissionIfNeeded()
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

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return

        val permission = Manifest.permission.POST_NOTIFICATIONS
        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            return
        }

        ActivityCompat.requestPermissions(
            this,
            arrayOf(permission),
            POST_NOTIFICATIONS_REQUEST_CODE
        )
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
