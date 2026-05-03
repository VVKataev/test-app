package com.ytdownloader.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.ytdownloader.R
import com.ytdownloader.databinding.ActivityMainBinding
import com.ytdownloader.service.DownloadService
import com.ytdownloader.util.YoutubeExtractor
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private var selectedQuality = 720
    
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val MANAGE_STORAGE_REQUEST_CODE = 101
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Инициализация NewPipe Extractor
        YoutubeExtractor.init()

        setupUI()
        checkPermissions()
    }

    private fun setupUI() {
        // Настройка Spinner с качеством
        val qualityAdapter = ArrayAdapter.createFromResource(
            this,
            R.array.quality_options,
            android.R.layout.simple_spinner_item
        )
        qualityAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.qualitySpinner.adapter = qualityAdapter
        
        binding.qualitySpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val qualityValues = resources.getIntArray(R.array.quality_values)
                selectedQuality = qualityValues[position]
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        // Обработчик кнопки скачивания
        binding.downloadButton.setOnClickListener {
            val url = binding.urlEditText.text.toString().trim()
            
            if (url.isEmpty()) {
                binding.urlEditText.error = getString(R.string.error_invalid_url)
                return@setOnClickListener
            }
            
            if (!isValidYouTubeUrl(url)) {
                binding.urlEditText.error = getString(R.string.error_invalid_url)
                Toast.makeText(this, R.string.error_invalid_url, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            
            binding.urlEditText.error = null
            
            if (checkPermissions()) {
                startDownload(url)
            }
        }

        // Обработчик кнопки открытия папки
        binding.openFolderButton.setOnClickListener {
            openDownloadsFolder()
        }
    }

    private fun isValidYouTubeUrl(url: String): Boolean {
        val patterns = listOf(
            "^(https?://)?(www\\.)?(youtube\\.com/watch\\?v=|youtu\\.be/)[a-zA-Z0-9_-]{11}",
            "^(https?://)?(www\\.)?youtube\\.com/embed/[a-zA-Z0-9_-]{11}",
            "^(https?://)?(www\\.)?youtube\\.com/v/[a-zA-Z0-9_-]{11}"
        )
        
        return patterns.any { url.matches(Regex(it)) }
    }

    private fun checkPermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (Environment.isExternalStorageManager()) {
                true
            } else {
                requestManageStoragePermission()
                false
            }
        } else {
            val readPermission = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
            val writePermission = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            
            if (readPermission != PackageManager.PERMISSION_GRANTED || 
                writePermission != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ),
                    PERMISSION_REQUEST_CODE
                )
                false
            } else {
                true
            }
        }
    }

    private fun requestManageStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:$packageName")
                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                startActivityForResult(intent, MANAGE_STORAGE_REQUEST_CODE)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                val url = binding.urlEditText.text.toString().trim()
                if (url.isNotEmpty() && isValidYouTubeUrl(url)) {
                    startDownload(url)
                }
            } else {
                Toast.makeText(this, "Необходимы разрешения для загрузки файлов", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        
        if (requestCode == MANAGE_STORAGE_REQUEST_CODE) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                val url = binding.urlEditText.text.toString().trim()
                if (url.isNotEmpty() && isValidYouTubeUrl(url)) {
                    startDownload(url)
                }
            }
        }
    }

    private fun startDownload(url: String) {
        showDownloadingState()
        
        DownloadService.startDownload(this, url, selectedQuality)
        
        lifecycleScope.launch {
            // Имитация обновления статуса
            kotlinx.coroutines.delay(1000)
            updateStatus(getString(R.string.status_downloading, 0))
        }
    }

    private fun showDownloadingState() {
        binding.downloadButton.isEnabled = false
        binding.progressBar.visibility = View.VISIBLE
        binding.statusText.visibility = View.VISIBLE
        binding.statusText.text = getString(R.string.status_waiting)
        binding.openFolderButton.visibility = View.GONE
    }

    private fun showCompletedState() {
        binding.downloadButton.isEnabled = true
        binding.progressBar.progress = 100
        binding.statusText.text = getString(R.string.status_completed)
        binding.openFolderButton.visibility = View.VISIBLE
    }

    private fun showErrorState(message: String) {
        binding.downloadButton.isEnabled = true
        binding.progressBar.visibility = View.GONE
        binding.statusText.text = getString(R.string.status_error, message)
        binding.openFolderButton.visibility = View.GONE
    }

    private fun updateStatus(status: String) {
        binding.statusText.text = status
    }

    private fun openDownloadsFolder() {
        val downloadDir = getExternalFilesDir("Downloads") ?: filesDir
        
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(Uri.fromFile(downloadDir), "resource/folder")
            }
            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "Не удалось открыть папку", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Проверка состояния разрешений при возврате в приложение
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
            // Разрешение получено
        }
    }
}
