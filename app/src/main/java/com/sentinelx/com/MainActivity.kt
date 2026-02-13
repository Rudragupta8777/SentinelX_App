package com.sentinelx.com

import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.Group
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.sentinelx.com.ui.fragments.ContactsFragment
import com.sentinelx.com.ui.fragments.KeypadFragment
import com.sentinelx.com.ui.fragments.RecentsFragment

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView
    private lateinit var setupLayout: View
    private lateinit var mainContentGroup: Group

    // List of all required permissions
    private val requiredPermissions = arrayOf(
        Manifest.permission.CALL_PHONE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.READ_CALL_LOG,
        Manifest.permission.READ_CONTACTS,
        Manifest.permission.ANSWER_PHONE_CALLS
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 1. Initialize Views
        bottomNav = findViewById(R.id.bottom_navigation)
        setupLayout = findViewById(R.id.layout_setup)
        mainContentGroup = findViewById(R.id.group_main_content)
        val btnGrant = findViewById<Button>(R.id.btn_grant_permissions)

        // 2. Setup Navigation Logic
        bottomNav.setOnItemSelectedListener { item ->
            var selectedFragment: Fragment? = null
            when (item.itemId) {
                R.id.nav_recents -> selectedFragment = RecentsFragment()
                R.id.nav_keypad -> selectedFragment = KeypadFragment()
                R.id.nav_contacts -> selectedFragment = ContactsFragment()
            }
            if (selectedFragment != null) {
                loadFragment(selectedFragment)
            }
            true
        }

        // 3. Check State on Launch
        if (isSetupComplete()) {
            showMainUI()
            handleIntent(intent) // Handle external dial clicks
        } else {
            showSetupUI()
        }

        // 4. Setup Button Click
        btnGrant.setOnClickListener {
            requestPermissionsChain()
        }
    }

    // --- LOGIC TO SWITCH UI ---

    private fun showMainUI() {
        setupLayout.visibility = View.GONE
        mainContentGroup.visibility = View.VISIBLE

        // Load default fragment if empty
        if (supportFragmentManager.findFragmentById(R.id.fragment_container) == null) {
            bottomNav.selectedItemId = R.id.nav_keypad
        }
    }

    private fun showSetupUI() {
        setupLayout.visibility = View.VISIBLE
        mainContentGroup.visibility = View.GONE
    }

    // --- PERMISSION & ROLE LOGIC ---

    private fun isSetupComplete(): Boolean {
        // 1. Check Permissions
        val permissionsGranted = requiredPermissions.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }

        // 2. Check Default Dialer Role
        val isDefaultDialer = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            roleManager.isRoleHeld(RoleManager.ROLE_DIALER)
        } else {
            // Basic check for older Android (less reliable but usually fine)
            val telecomManager = getSystemService(Context.TELECOM_SERVICE) as android.telecom.TelecomManager
            telecomManager.defaultDialerPackage == packageName
        }

        return permissionsGranted && isDefaultDialer
    }

    private fun requestPermissionsChain() {
        // Step 1: Request Runtime Permissions
        permissionLauncher.launch(requiredPermissions)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.all { it.value }
        if (allGranted) {
            // Step 2: Request Default Dialer Role
            requestDefaultDialerRole()
        } else {
            Toast.makeText(this, "Permissions are required to use Sentinel-X", Toast.LENGTH_SHORT).show()
        }
    }

    private fun requestDefaultDialerRole() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(Context.ROLE_SERVICE) as RoleManager
            if (roleManager.isRoleAvailable(RoleManager.ROLE_DIALER)) {
                val intent = roleManager.createRequestRoleIntent(RoleManager.ROLE_DIALER)
                roleLauncher.launch(intent)
            }
        } else {
            val intent = Intent(android.telecom.TelecomManager.ACTION_CHANGE_DEFAULT_DIALER)
            intent.putExtra(android.telecom.TelecomManager.EXTRA_CHANGE_DEFAULT_DIALER_PACKAGE_NAME, packageName)
            startActivity(intent) // Older android doesn't use result launcher for this well
            // We just assume they did it or check in onResume
        }
    }

    private val roleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            Toast.makeText(this, "Sentinel-X Activated!", Toast.LENGTH_LONG).show()
            showMainUI()
        } else {
            Toast.makeText(this, "Please set Sentinel-X as default to continue.", Toast.LENGTH_SHORT).show()
        }
    }

    // Check again when returning from settings/other apps
    override fun onResume() {
        super.onResume()
        if (isSetupComplete() && setupLayout.visibility == View.VISIBLE) {
            showMainUI()
        }
    }

    // --- INTENT HANDLING (From previous step) ---

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (isSetupComplete()) {
            handleIntent(intent)
        }
    }

    private fun handleIntent(intent: Intent?) {
        val action = intent?.action
        val data = intent?.data

        if (Intent.ACTION_DIAL == action || Intent.ACTION_VIEW == action) {
            bottomNav.selectedItemId = R.id.nav_keypad
            val number = data?.schemeSpecificPart

            if (number != null) {
                val fragment = KeypadFragment()
                val bundle = Bundle()
                bundle.putString("PREFILLED_NUMBER", number)
                fragment.arguments = bundle
                loadFragment(fragment)
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragment_container, fragment)
            .commit()
    }
}