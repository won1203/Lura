package com.example.lura

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavController
import androidx.navigation.NavOptions
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.example.lura.alarm.AlarmAppVisibility
import com.example.lura.databinding.ActivityMainBinding
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var appBarConfiguration: AppBarConfiguration
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navController = findNavController(R.id.nav_host_fragment_content_main)
        appBarConfiguration = AppBarConfiguration(
            setOf(
                R.id.homeFragment,
                R.id.alarmSetupFragment,
                R.id.alarmHistoryFragment
            )
        )
        setupActionBarWithNavController(navController, appBarConfiguration)
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

    override fun onSupportNavigateUp(): Boolean {
        val navController = findNavController(R.id.nav_host_fragment_content_main)
        return navController.navigateUp(appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    private fun setupBottomNavigation(navController: NavController) {
        val bottomNavigation = findViewById<BottomNavigationView>(R.id.bottom_navigation)
        bottomNavigation.setOnItemSelectedListener { item ->
            navigateBottomTab(navController, item)
        }
        bottomNavigation.setOnItemReselectedListener {
            // Keeping reselection idempotent preserves user-entered alarm settings.
        }
        navController.addOnDestinationChangedListener { _, destination, _ ->
            bottomNavigation.menu.findItem(destination.id)?.isChecked = true
        }
    }

    private fun navigateBottomTab(navController: NavController, item: MenuItem): Boolean {
        if (navController.currentDestination?.id == item.itemId) {
            return true
        }

        return when (item.itemId) {
            R.id.homeFragment -> {
                navigateToHomeTab(navController)
                true
            }
            R.id.alarmSetupFragment,
            R.id.alarmHistoryFragment -> {
                navController.navigate(item.itemId, null, bottomTabNavOptions())
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
        private const val POST_NOTIFICATIONS_REQUEST_CODE = 4101
    }
}
