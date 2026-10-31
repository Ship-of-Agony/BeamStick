package com.example.beamsticklauncher

import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.io.File

class MainActivity : AppCompatActivity() {

    private lateinit var tvAppBranding: TextView
    private lateinit var tvCurrentPath: TextView
    private lateinit var btnSettings: Button
    private lateinit var rvFiles: RecyclerView
    private lateinit var tvEmptyHint: TextView
    private lateinit var scrollDriveSelector: HorizontalScrollView
    private lateinit var containerDrives: LinearLayout
    private lateinit var layoutStorageBar: LinearLayout
    private lateinit var tvDriveName: TextView
    private lateinit var tvDriveCapacity: TextView
    private lateinit var pbStorageUsage: ProgressBar

    private lateinit var settings: AppSettings
    private lateinit var fileAdapter: FileAdapter

    private var availableDrives = listOf<UsbDrive>()
    private var activeDrive: UsbDrive? = null
    private var currentDir: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        settings = AppSettings(this)

        initViews()
        setupRecyclerView()
        checkAndRequestPermissions()
    }

    override fun onResume() {
        super.onResume()
        refreshDrives()
    }

    private fun initViews() {
        tvAppBranding = findViewById(R.id.tvAppBranding)
        tvCurrentPath = findViewById(R.id.tvCurrentPath)
        btnSettings = findViewById(R.id.btnSettings)
        rvFiles = findViewById(R.id.rvFiles)
        tvEmptyHint = findViewById(R.id.tvEmptyHint)
        scrollDriveSelector = findViewById(R.id.scrollDriveSelector)
        containerDrives = findViewById(R.id.containerDrives)
        layoutStorageBar = findViewById(R.id.layoutStorageBar)
        tvDriveName = findViewById(R.id.tvDriveName)
        tvDriveCapacity = findViewById(R.id.tvDriveCapacity)
        pbStorageUsage = findViewById(R.id.pbStorageUsage)

        val versionName = try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                packageManager.getPackageInfo(packageName, 0)
            }
            pInfo.versionName ?: "1.0"
        } catch (e: Exception) {
            "1.0"
        }
        tvAppBranding.text = "by Ship of Agony LABs (v$versionName)"

        btnSettings.setOnClickListener {
            SettingsDialog(this, settings).show()
        }
    }

    private fun setupRecyclerView() {
        fileAdapter = FileAdapter(
            files = emptyList(),
            onItemClick = { selectedFile ->
                if (selectedFile.isDirectory) {
                    openDirectory(selectedFile)
                } else {
                    FileOpener.openFile(this, selectedFile, settings)
                }
            },
            onItemLongClick = { selectedFile ->
                MediaInfoDialog(this, selectedFile).show()
            }
        )
        rvFiles.layoutManager = GridLayoutManager(this, 2)
        rvFiles.adapter = fileAdapter
    }

    private fun refreshDrives() {
        availableDrives = FileUtils.scanUsbDrives(this)

        if (availableDrives.isEmpty()) {
            activeDrive = null
            currentDir = null
            fileAdapter.updateList(emptyList())
            tvCurrentPath.text = getString(R.string.no_usb_connected)
            tvEmptyHint.text = getString(R.string.insert_usb_hint)
            tvEmptyHint.visibility = View.VISIBLE
            layoutStorageBar.visibility = View.GONE
            scrollDriveSelector.visibility = View.GONE
            return
        }

        if (activeDrive == null || availableDrives.none { it.rootDir.path == activeDrive?.rootDir?.path }) {
            selectDrive(availableDrives.first())
        } else {
            renderDriveSelector()
            renderStorageBar()
        }
    }

    private fun selectDrive(drive: UsbDrive) {
        activeDrive = drive
        currentDir = drive.rootDir
        renderDriveSelector()
        renderStorageBar()
        openDirectory(drive.rootDir)
    }

    private fun renderDriveSelector() {
        containerDrives.removeAllViews()
        if (availableDrives.size > 1) {
            scrollDriveSelector.visibility = View.VISIBLE
            val createdButtons = mutableListOf<Button>()

            for ((index, drive) in availableDrives.withIndex()) {
                val isSelected = (drive == activeDrive)
                val btn = Button(this).apply {
                    id = View.generateViewId()
                    text = drive.label
                    isAllCaps = false
                    textSize = 13f
                    isFocusable = true
                    isFocusableInTouchMode = false
                    isClickable = true
                    stateListAnimator = null

                    setTextColor(if (isSelected) android.graphics.Color.WHITE else android.graphics.Color.parseColor("#94A3B8"))
                    setBackgroundResource(if (isSelected) R.drawable.bg_tile_focused else R.drawable.bg_tile_normal)
                    setPadding(32, 12, 32, 12)

                    setOnFocusChangeListener { v, hasFocus ->
                        if (hasFocus) {
                            v.setBackgroundResource(R.drawable.bg_tile_focused)
                            v.animate().scaleX(1.05f).scaleY(1.05f).setDuration(120).start()
                        } else {
                            v.setBackgroundResource(if (drive == activeDrive) R.drawable.bg_tile_focused else R.drawable.bg_tile_normal)
                            v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(120).start()
                        }
                    }

                    setOnClickListener { selectDrive(drive) }
                    nextFocusDownId = R.id.rvFiles
                }
                createdButtons.add(btn)
                val params = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                ).apply { marginEnd = 16 }
                containerDrives.addView(btn, params)
            }

            // Exakte D-Pad Nachbarschaft zwischen den Tabs verketten:
            for (i in createdButtons.indices) {
                if (i > 0) {
                    createdButtons[i].nextFocusLeftId = createdButtons[i - 1].id
                }
                if (i < createdButtons.size - 1) {
                    createdButtons[i].nextFocusRightId = createdButtons[i + 1].id
                } else {
                    createdButtons[i].nextFocusRightId = R.id.btnSettings
                }
            }
        } else {
            scrollDriveSelector.visibility = View.GONE
        }
    }

    private fun renderStorageBar() {
        val drive = activeDrive ?: return
        layoutStorageBar.visibility = View.VISIBLE
        tvDriveName.text = drive.label
        tvDriveCapacity.text = drive.formatCapacity(this)
        pbStorageUsage.progress = drive.percentUsed
    }

    private fun openDirectory(dir: File) {
        currentDir = dir
        tvCurrentPath.text = dir.absolutePath

        val files = dir.listFiles()?.filter { !it.isHidden }?.sortedWith(
            compareBy({ !it.isDirectory }, { it.name.lowercase() })
        ) ?: emptyList()

        fileAdapter.updateList(files)
        tvEmptyHint.visibility = if (files.isEmpty()) View.VISIBLE else View.GONE
        if (files.isEmpty()) {
            tvEmptyHint.text = getString(R.string.folder_empty)
        }
    }

    override fun onBackPressed() {
        val activeRoot = activeDrive?.rootDir
        val current = currentDir

        if (current != null && activeRoot != null && current.absolutePath != activeRoot.absolutePath) {
            val parent = current.parentFile
            if (parent != null && parent.canRead()) {
                openDirectory(parent)
                return
            }
        }
        super.onBackPressed()
    }

    private fun checkAndRequestPermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (!Environment.isExternalStorageManager()) {
                showPermissionDialog()
            }
        }
    }

    private fun showPermissionDialog() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(getString(R.string.perm_dialog_title))
            .setMessage(getString(R.string.perm_dialog_msg))
            .setPositiveButton(getString(R.string.perm_btn_grant)) { _, _ ->
                requestStorageManagerAccess()
            }
            .setNegativeButton(getString(R.string.perm_btn_later), null)
            .setCancelable(false)
            .show()
    }

    private fun requestStorageManagerAccess() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
                return
            } catch (ignored: Exception) {
            }

            try {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivity(intent)
                return
            } catch (ignored: Exception) {
            }

            try {
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.parse("package:$packageName")
                }
                startActivity(intent)
            } catch (e: ActivityNotFoundException) {
                Toast.makeText(this, "Settings could not be opened.", Toast.LENGTH_LONG).show()
            }
        }
    }
}