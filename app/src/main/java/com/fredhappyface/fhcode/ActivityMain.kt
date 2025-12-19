package com.fredhappyface.fhcode

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.webkit.MimeTypeMap
import android.widget.EditText
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.NestedScrollView
import androidx.preference.PreferenceManager
import com.fredhappyface.fhcode.languagerules.CPP
import com.fredhappyface.fhcode.languagerules.CSharp
import com.fredhappyface.fhcode.languagerules.Go
import com.fredhappyface.fhcode.languagerules.JSON
import com.fredhappyface.fhcode.languagerules.LanguageRules
import com.fredhappyface.fhcode.languagerules.Java
import com.fredhappyface.fhcode.languagerules.TSJS
import com.fredhappyface.fhcode.languagerules.PHP
import com.fredhappyface.fhcode.languagerules.Python
import com.fredhappyface.fhcode.languagerules.Ruby
import com.fredhappyface.fhcode.languagerules.Swift
import com.fredhappyface.fhcode.languagerules.XML
import java.io.BufferedReader
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader

private const val MAX_FILE_SIZE = 1024 * 1024 // 1Mb

/**
 * ActivityMain class inherits from the ActivityThemable class - provides the settings view
 */
class ActivityMain : ActivityThemable() {
	/**
	 * Storage of private vars. These being _uri (stores uri of opened file); _createFileRequestCode
	 * (custom request code); _readRequestCode (request code for reading a file)
	 */
	private var currentTextSize = 0
	private var uri: String? = null
	private var languageID = "java"
	private var isModified = false
	private var textWatcher: TextWatcher? = null
	private var filename: String? = null
	private val undoStack = mutableListOf<String>()
	private val redoStack = mutableListOf<String>()
	private var isUndoRedoOperation = false

	/**
	 * Override the onCreate method from ActivityThemable adding the activity_main view and configuring
	 * the codeEditText, the textHighlight and the initial text
	 *
	 * @param savedInstanceState saved state
	 */
	override fun onCreate(savedInstanceState: Bundle?) {
		super.onCreate(savedInstanceState)
		setContentView(R.layout.activity_main)

		// Get saved state
		this.uri = savedInstanceState?.getString("_uri", null)
		this.languageID = savedInstanceState?.getString("_languageID", "java").toString()
		this.isModified = savedInstanceState?.getBoolean("_isModified", false) ?: false
		this.filename = savedInstanceState?.getString("_filename", null)
		if (this.isModified) {
			findViewById<TextView>(R.id.statusModified).text = "Modified"
		}
		// Update title with filename or "untitled"
		updateTitle()
		// Set up correct colour
		var colours: Colours = ColoursDark()
		if (this.currentTheme == 0) {
			colours = ColoursLight()
		}
		// Set up correct language
		var languageRules: LanguageRules = Java()
		when (this.languageID) {
			"cpp", "c", "h", "hpp" -> languageRules = CPP()
			"csharp", "cs" -> languageRules = CSharp()
			"go" -> languageRules = Go()
			"java" -> languageRules = Java()
			"json", "jsonc" -> languageRules = JSON()
			"php", "php3", "php4", "php5", "phtml" -> languageRules = PHP()
			"py", "pyc", "pyo" -> languageRules = Python()
			"rb" -> languageRules = Ruby()
			"swift" -> languageRules = Swift()
			"ts", "js" -> languageRules = TSJS()
			"xml", "xsd", "xsl" -> languageRules = XML()
		}


		val codeEditText: EditText = findViewById(R.id.codeHighlight)
		codeEditText.setOnClickListener { updateCursorPosition() }
		codeEditText.setOnKeyListener { _, _, _ -> updateCursorPosition(); false }
		val textHighlight = TextHighlight(
			codeEditText,
			languageRules,
			colours
		)
		textHighlight.start()
		codeEditText.setText(R.string.blank_file_text)

		// Apply text size
		this.sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
		this.currentTextSize = this.sharedPreferences.getInt("text", 18)
		codeEditText.textSize = this.currentTextSize.toFloat()

		if (savedInstanceState == null && intent.getStringExtra("action") == "open") {
			startFileOpen()
		}
	}

	/**
	 * Triggered when an activity is resumed. If the text size differs from the current text size,
	 * then the activity is recreated
	 */
	override fun onResume() {
		super.onResume()
		val textSize = this.sharedPreferences.getInt("text", 18)
		if (this.currentTextSize != textSize) {
			this.currentTextSize = textSize
			recreate()
		}

		val codeEditText: EditText = findViewById(R.id.codeHighlight)
		if (textWatcher == null) {
			textWatcher = object : TextWatcher {
				private var beforeText = ""
				
				override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                    updateCursorPosition()
					if (!isUndoRedoOperation) {
						beforeText = s.toString()
					}
                }
				override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
				override fun afterTextChanged(s: Editable?) {
					if (!isUndoRedoOperation) {
						// Save state for undo
						if (beforeText != s.toString()) {
							undoStack.add(beforeText)
							// Limit stack size to prevent memory issues
							if (undoStack.size > 100) {
								undoStack.removeAt(0)
							}
							// Clear redo stack when new edit is made
							redoStack.clear()
						}
					}
					isModified = true
					findViewById<TextView>(R.id.statusModified).text = "Modified"
					updateCursorPosition()
				}
			}
			codeEditText.addTextChangedListener(textWatcher)
		}
	}

	/**
	 * Override the onCreateOptionsMenu method (used to create the overflow menu - see three dotted
	 * menu on the title bar)
	 *
	 * @param menu Menu - this is the popup menu (containing a series of actions)
	 * @return Boolean - success!
	 */
	override fun onCreateOptionsMenu(menu: Menu): Boolean {
		val inflater: MenuInflater = menuInflater
		inflater.inflate(R.menu.main_menu, menu)
		return true
	}

	/**
	 * Override the onOptionsItemSelected method. This is essentially a callback method triggered when
	 * the end user selects a menu item. Here we filter the item/ action selection and trigger a
	 * corresponding action. E.g. action_open -> startFileOpen()
	 *
	 * @param item MenuItem - this is the item/ action that the user taps
	 * @return Boolean - success!
	 */
	override fun onOptionsItemSelected(item: MenuItem): Boolean {
		// Handle item selection
		return when (item.itemId) {
			R.id.action_undo -> {
				doUndo(); true
			}

			R.id.action_redo -> {
				doRedo(); true
			}

			R.id.action_tab -> {
				insertTab(); true
			}

			R.id.action_left_brace -> {
				insertLeftBrace(); true
			}

			R.id.action_right_brace -> {
				insertRightBrace(); true
			}

			R.id.action_new_file -> {
				doNewFile(); true
			}

			R.id.action_open -> {
				startFileOpen(); true
			}

			R.id.action_save -> {
				doFileSave(); true
			}

			R.id.action_save_as -> {
				startFileSaveAs(); true
			}

			R.id.action_go_to_line -> {
				doGoToLine(); true
			}

			R.id.action_settings -> {
				startActivity(Intent(this, ActivitySettings::class.java)); true
			}

			R.id.action_about -> {
				startActivity(Intent(this, ActivityAbout::class.java)); true
			}

			else -> super.onOptionsItemSelected(item)
		}
	}

	/**
	 * Override onSaveInstanceState to save the _languageID and _uri when recreating the activity
	 *
	 * @param outState: Bundle
	 */
	override fun onSaveInstanceState(outState: Bundle) {
		super.onSaveInstanceState(outState)
		outState.putString("_languageID", this.languageID)
		outState.putString("_uri", this.uri)
		outState.putBoolean("_isModified", this.isModified)
		outState.putString("_filename", this.filename)
	}

	/**
	 * Somewhat unintuitive way to obtain the file extension from a URI as android often uses non
	 * file path URIs
	 *
	 * @param uri
	 * @return String file extension (short) eg. py for a python file
	 */
	private fun getExtFromURI(uri: Uri?): String {
		if (uri != null) {
			val cursor = contentResolver.query(uri, null, null, null, null)
			if (cursor != null) {
				cursor.moveToFirst()
				val ext = cursor.getString(2) // Get the file name
				cursor.close()
				return ext.split(".").last()
			}
			return MimeTypeMap.getSingleton().getExtensionFromMimeType(
				contentResolver.getType(uri)
			).toString()
		}
		return "java"
	}

	/**
	 * Get the filename from a URI
	 *
	 * @param uri
	 * @return String filename or null
	 */
	private fun getFilenameFromURI(uri: Uri?): String? {
		if (uri != null) {
			contentResolver.query(uri, null, null, null, null)?.use { cursor ->
				cursor.moveToFirst()
				val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
				if (nameIndex >= 0) {
					return cursor.getString(nameIndex)
				}
			}
		}
		return null
	}

	/**
	 * Update the action bar title with the current filename
	 */
	private fun updateTitle() {
		supportActionBar?.title = filename ?: "untitled"
	}

	/**
	 * Show a 'saved' dialog. In a function as its reused a couple of times
	 *
	 */
	private fun showDialogMessageSave() {
		val alertDialog: AlertDialog = AlertDialog.Builder(this, R.style.DialogTheme).create()
		alertDialog.setTitle(getString(R.string.dialog_saved_title))
		alertDialog.setButton(
			AlertDialog.BUTTON_NEUTRAL, getString(R.string.dialog_saved_button)
		) { dialog, _ ->
			dialog.dismiss()
			this.isModified = false
			recreate()
		}
		alertDialog.show()
	}

	/**
	 * Update the cursor position in the status bar
	 */
	private fun updateCursorPosition() {
		val codeEditText: EditText = findViewById(R.id.codeHighlight)
		val statusCursor: TextView = findViewById(R.id.statusCursor)
		val pos = codeEditText.selectionStart
		val layout = codeEditText.layout
		if (layout != null) {
			val line = layout.getLineForOffset(pos)
			val col = pos - layout.getLineStart(line)
			statusCursor.text = "Ln ${line + 1}, Col ${col + 1}"
		}
	}

	/**
	 * Show a dialog to go to a specific line
	 */
	private fun doGoToLine() {
		val codeEditText: EditText = findViewById(R.id.codeHighlight)
		val input = EditText(this)
		input.inputType = InputType.TYPE_CLASS_NUMBER
		val alertDialog = AlertDialog.Builder(this, R.style.DialogTheme).create()
		alertDialog.setTitle("Go to line")
		alertDialog.setView(input)
		alertDialog.setButton(AlertDialog.BUTTON_POSITIVE, "Go") { dialog, _ ->
			val line = input.text.toString().toIntOrNull()
			if (line != null && line > 0 && line <= codeEditText.lineCount) {
				val layout = codeEditText.layout
				val pos = layout.getLineStart(line - 1)
				codeEditText.setSelection(pos)
                updateCursorPosition()
			}
			dialog.dismiss()
		}
		alertDialog.setButton(AlertDialog.BUTTON_NEGATIVE, "Cancel") { dialog, _ -> dialog.dismiss() }
		alertDialog.show()
	}

	/**
	 * Undo the last text change
	 */
	private fun doUndo() {
		if (undoStack.isNotEmpty()) {
			val codeEditText: EditText = findViewById(R.id.codeHighlight)
			val currentText = codeEditText.text.toString()
			val previousText = undoStack.removeAt(undoStack.size - 1)
			
			redoStack.add(currentText)
			
			isUndoRedoOperation = true
			codeEditText.setText(previousText)
			codeEditText.setSelection(codeEditText.text.length)
			isUndoRedoOperation = false
		}
	}

	/**
	 * Redo the last undone text change
	 */
	private fun doRedo() {
		if (redoStack.isNotEmpty()) {
			val codeEditText: EditText = findViewById(R.id.codeHighlight)
			val currentText = codeEditText.text.toString()
			val nextText = redoStack.removeAt(redoStack.size - 1)
			
			undoStack.add(currentText)
			
			isUndoRedoOperation = true
			codeEditText.setText(nextText)
			codeEditText.setSelection(codeEditText.text.length)
			isUndoRedoOperation = false
		}
	}

	/**
	 * Insert a tab character at the current cursor position
	 */
	private fun insertTab() {
		val codeEditText: EditText = findViewById(R.id.codeHighlight)
		val cursorPosition = codeEditText.selectionStart
		val text = codeEditText.text
		text.insert(cursorPosition, "\t")
	}

	/**
	 * Insert a left curly brace at the current cursor position
	 */
	private fun insertLeftBrace() {
		val codeEditText: EditText = findViewById(R.id.codeHighlight)
		val cursorPosition = codeEditText.selectionStart
		val text = codeEditText.text
		text.insert(cursorPosition, "{")
	}

	/**
	 * Insert a right curly brace at the current cursor position
	 */
	private fun insertRightBrace() {
		val codeEditText: EditText = findViewById(R.id.codeHighlight)
		val cursorPosition = codeEditText.selectionStart
		val text = codeEditText.text
		text.insert(cursorPosition, "}")
	}


    /**
	 * Get the initial URI for the file picker
	 *
	 * @return Uri? - the initial URI
	 */
	private fun getInitialUri(): Uri? {
		// Try to get Documents directory first (more user-accessible)
		val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
		val hfcodeDir = File(documentsDir, "hfcode")

		// Create directory if it doesn't exist
		if (!hfcodeDir.exists()) {
			if (!hfcodeDir.mkdirs()) {
				// If Documents/hfcode fails, try app-specific external storage
				val externalFilesDir = getExternalFilesDir(null) ?: return null
				val appDir = File(externalFilesDir, "hfcode")
				if (!appDir.exists()) {
					if (!appDir.mkdirs()) {
						return null
					}
				}
				// Try to build URI for app-specific directory
				return buildDocumentUri(appDir)
			}
		}

		// Build URI for Documents/hfcode directory
		return buildDocumentUri(hfcodeDir)
	}

	/**
	 * Build a DocumentsContract URI from a File path
	 *
	 * @param dir File - the directory to build URI for
	 * @return Uri? - the DocumentsContract URI or null if failed
	 */
	private fun buildDocumentUri(dir: File): Uri? {
		try {
			val externalStorageRoot = Environment.getExternalStorageDirectory()
			val path = dir.absolutePath
			if (path.startsWith(externalStorageRoot.absolutePath)) {
				val relativePath = path.substring(externalStorageRoot.absolutePath.length)
					.trim('/')
				val documentId = "primary:$relativePath"
				return DocumentsContract.buildDocumentUri(
					"com.android.externalstorage.documents",
					documentId
				)
			}
		} catch (e: Exception) {
			e.printStackTrace()
		}
		return null
	}

	/**
	 * Call this when the user clicks menu -> new file
	 *
	 */
	private fun doNewFile() {
		val createNewFile = {
			val codeEditText: EditText = findViewById(R.id.codeHighlight)
			codeEditText.setText(R.string.blank_file_text)
			this.languageID = "java"
			this.uri = null
			this.filename = null
			this.isModified = false
			this.undoStack.clear()
			this.redoStack.clear()
			recreate()
		}

		if (isModified) {
			val alertDialog: AlertDialog = AlertDialog.Builder(this, R.style.DialogTheme).create()
			alertDialog.setTitle(getString(R.string.dialog_unsaved_title))
			alertDialog.setMessage(getString(R.string.dialog_unsaved_message))
			alertDialog.setButton(
				AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_unsaved_cancel)
			) { dialog, _ -> dialog.dismiss() }
			alertDialog.setButton(
				AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_unsaved_confirm)
			) { dialog, _ ->
				dialog.dismiss()
				createNewFile()
			}
			alertDialog.show()
		} else {
			val alertDialog: AlertDialog = AlertDialog.Builder(this, R.style.DialogTheme).create()
			alertDialog.setTitle(getString(R.string.dialog_new_title))
			// Cancel/ No - Do nothing
			alertDialog.setButton(
				AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_new_cancel)
			) { dialog, _ -> dialog.dismiss(); }
			// Confirm/ Yes - Overwrite text, reset language id and uri and refresh
			alertDialog.setButton(
				AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_new_confirm)
			) { dialog, _ ->
				dialog.dismiss()
				createNewFile()
			}
			alertDialog.show()
		}
	}

	/**
	 * Call this when the user clicks menu -> save
	 *
	 */
	private fun doFileSave() {
		if (this.uri != null) {
			writeTextToUri(Uri.parse(this.uri ?: return))
			showDialogMessageSave()
		} else {
			startFileSaveAs()
		}
	}

	/**
	 * Handles ACTION_OPEN_DOCUMENT result and sets this.uri, mLanguageID and codeEditText
	 */
	private val completeFileOpen =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				this.uri = result.data?.data.toString()
				val parsedUri = Uri.parse(this.uri)
				this.languageID = getExtFromURI(parsedUri)
				this.filename = getFilenameFromURI(parsedUri)
				val codeEditText: EditText = findViewById(R.id.codeHighlight)
				codeEditText.setText(readTextFromUri(parsedUri))
				this.isModified = false
				this.undoStack.clear()
				this.redoStack.clear()
				recreate()
			}
		}

	/**
	 * Call this when the user clicks menu -> open
	 *
	 */
	fun startFileOpen() {
		val launchPicker = {
			val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
			intent.addCategory(Intent.CATEGORY_OPENABLE)
			intent.type = "*/*"
            getInitialUri()?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }

			//val uri = getInitialUri()
			//if (uri != null) {
			//	intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
			//}
			completeFileOpen.launch(intent)
		}

		if (isModified) {
			val alertDialog: AlertDialog = AlertDialog.Builder(this, R.style.DialogTheme).create()
			alertDialog.setTitle(getString(R.string.dialog_unsaved_title))
			alertDialog.setMessage(getString(R.string.dialog_unsaved_message))
			alertDialog.setButton(
				AlertDialog.BUTTON_NEGATIVE, getString(R.string.dialog_unsaved_cancel)
			) { dialog, _ -> dialog.dismiss() }
			alertDialog.setButton(
				AlertDialog.BUTTON_POSITIVE, getString(R.string.dialog_unsaved_confirm)
			) { dialog, _ ->
				dialog.dismiss()
				launchPicker()
			}
			alertDialog.show()
		} else {
			launchPicker()
		}
	}

	/**
	 * Handles ACTION_CREATE_DOCUMENT result and sets this.uri, mLanguageID and triggers writeTextToUri
	 */
	private val completeFileSaveAs =
		registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
			if (result.resultCode == Activity.RESULT_OK) {
				this.uri = result.data?.data.toString()
				val parsedUri = Uri.parse(this.uri)
				this.languageID = getExtFromURI(parsedUri)
				this.filename = getFilenameFromURI(parsedUri)
				updateTitle()
				writeTextToUri(parsedUri)
				showDialogMessageSave()
			}
		}

	private fun startFileSaveAs() {
		val intent = Intent(Intent.ACTION_CREATE_DOCUMENT)
		intent.addCategory(Intent.CATEGORY_OPENABLE)
		intent.type = "*/*"
        getInitialUri()?.let { intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, it) }
		//val uri = getInitialUri()
		//if (uri != null) {
		//	intent.putExtra(DocumentsContract.EXTRA_INITIAL_URI, uri)
		//}
		completeFileSaveAs.launch(intent)
	}

	/**
	 * Write the file text to the URI
	 *
	 * @param uri Uri - the uri of the file we are going to overwrite
	 * @return Boolean - success/ failure!
	 */
	private fun writeTextToUri(uri: Uri): Boolean {
		val codeEditText: EditText = findViewById(R.id.codeHighlight)
		try {
			contentResolver.openFileDescriptor(uri, "rwt")?.use { it ->
				FileOutputStream(it.fileDescriptor).use {
					val bytes = codeEditText.text.toString()
						.toByteArray()
					it.write(bytes, 0, bytes.size)
				}
			}
			return true
		} catch (e: FileNotFoundException) {
			e.printStackTrace()
		} catch (e: IOException) {
			e.printStackTrace()
		} catch (e: SecurityException) {
			e.printStackTrace()
		}
		return false
	}

	/**
	 * Read the file text from the URI
	 *
	 * @param uri Uri - the uri of the file we are going to read
	 * @return String - contents of the file (decoded per readLines())
	 */
	private fun readTextFromUri(uri: Uri): String {
		contentResolver.query(uri, null, null, null, null).use { cursor ->
			val sizeIdx = cursor?.getColumnIndex(OpenableColumns.SIZE)
			cursor?.moveToFirst()
			val size = sizeIdx?.let { cursor.getLong(it) }
			if ((size ?: 0) > MAX_FILE_SIZE) {
				return "File too large! (over $MAX_FILE_SIZE bytes)\n"
			}
		}
		val inputStream: InputStream? = contentResolver.openInputStream(uri)
		val reader = BufferedReader(InputStreamReader(inputStream))
		return reader.readLines().joinToString("\n")
	}
}
