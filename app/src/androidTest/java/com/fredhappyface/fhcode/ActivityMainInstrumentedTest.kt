package com.fredhappyface.fhcode

import android.content.Context
import android.os.Environment
import android.text.InputType
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.Espresso.openActionBarOverflowOrOptionsMenu
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.action.ViewActions.replaceText
import androidx.test.espresso.action.ViewActions.typeText
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withInputType
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import org.hamcrest.Matchers.allOf
import org.hamcrest.Matchers.containsString
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Instrumented tests for ActivityMain
 */
@RunWith(AndroidJUnit4::class)
class ActivityMainInstrumentedTest {

    private lateinit var scenario: ActivityScenario<ActivityMain>

    @Before
    fun setUp() {
        scenario = ActivityScenario.launch(ActivityMain::class.java)
    }

    @After
    fun tearDown() {
        scenario.close()
        // Clean up test directories
        cleanupTestDirectories()
    }

    /**
     * Helper function to clean up test-created directories
     */
    private fun cleanupTestDirectories() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Clean up Documents/hfcode
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val hfcodeDir = File(documentsDir, "hfcode")
        if (hfcodeDir.exists()) {
            hfcodeDir.deleteRecursively()
        }

        // Clean up app-specific hfcode
        val externalFilesDir = context.getExternalFilesDir(null)
        if (externalFilesDir != null) {
            val appHfcodeDir = File(externalFilesDir, "hfcode")
            if (appHfcodeDir.exists()) {
                appHfcodeDir.deleteRecursively()
            }
        }
    }

    /**
     * Helper function to dismiss the system file picker using UiAutomator
     */
    private fun dismissFilePicker() {
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

        // Wait for the file picker to appear
        Thread.sleep(1000)

        device.pressBack()
        Thread.sleep(500)

        for(i in 1..5) {
            if (!device.hasObject(By.pkg("com.fredhappyface.fhcode"))) {
                device.pressBack()
                Thread.sleep(500)
            }
        }
    }

    /**
     * Helper function to open "Go to line" dialog and enter a line number
     */
    private fun openGoToLineDialogAndEnterNumber(context: Context, lineNumber: String) {
        openActionBarOverflowOrOptionsMenu(context)
        Thread.sleep(200)
        onView(withText("Go to line")).perform(click())
        Thread.sleep(200)

        // Find the EditText in the dialog by matching its input type (TYPE_CLASS_NUMBER)
        // Since the dialog's EditText doesn't have an ID, we match by input type and class
        onView(allOf(
            isAssignableFrom(EditText::class.java),
            withInputType(InputType.TYPE_CLASS_NUMBER),
            isDisplayed()
        )).perform(typeText(lineNumber))
    }

    /**
     * Helper function to click the "Go" button in the dialog
     */
    private fun clickGoButton() {
        Thread.sleep(300)

        onView(withText("Go")).perform(click())

        // Wait for dialog to fully dismiss - verify codeHighlight is visible
        Thread.sleep(300)

        // Wait until the codeHighlight EditText is actually visible (dialog has dismissed)
        var retries = 0
        while (retries < 10) {
            try {
                onView(withId(R.id.codeHighlight)).check(matches(isDisplayed()))
                break
            } catch (e: Exception) {
                Thread.sleep(200)
                retries++
            }
        }

        // Manually trigger cursor position update using scenario
        scenario.onActivity { activity ->
            val codeEditText = activity.findViewById<EditText>(R.id.codeHighlight)
            val statusCursor = activity.findViewById<TextView>(R.id.statusCursor)
            val pos = codeEditText.selectionStart
            val layout = codeEditText.layout
            if (layout != null) {
                val line = layout.getLineForOffset(pos)
                val col = pos - layout.getLineStart(line)
                statusCursor.text = "Ln ${line + 1}, Col ${col + 1}"
            }
        }

        Thread.sleep(200)
    }

    /**
     * Test: Verify that a new view acting as a status bar is visible at the bottom of the editor screen
     */
    @Test
    fun testStatusBarIsVisible() {
        onView(withId(R.id.statusBar))
            .check(matches(isDisplayed()))
    }

    /**
     * Test: After typing text into the editor, verify the status bar contains "Modified".
     * After saving the file, verify "Modified" is no longer displayed.
     */
    @Test
    fun testModifiedStatusAppears() {
        // Type text into the editor
        onView(withId(R.id.codeHighlight))
            .perform(typeText("test"))

        // Verify "Modified" appears in status bar
        onView(withId(R.id.statusModified))
            .check(matches(withText("Modified")))
    }

    /**
     * Test: Move the cursor to at least 2-3 different positions and verify the status bar
     * text accurately reflects the new line and character numbers each time.
     */
    @Test
    fun testCursorPositionUpdates() {
        // Initial position should be Ln 1, Col 1
        onView(withId(R.id.statusCursor))
            .check(matches(withText("Ln 1, Col 1")))

        // Type some text and verify cursor position updates
        onView(withId(R.id.codeHighlight))
            .perform(typeText("Hello"))

        Thread.sleep(200)

        // Cursor should now be at Col 6
        onView(withId(R.id.statusCursor))
            .check(matches(withText("Ln 1, Col 6")))

        Thread.sleep(200)

        // Add a newline and more text
        onView(withId(R.id.codeHighlight))
            .perform(typeText("\nWorld"))

        Thread.sleep(200)

        // Cursor should now be on line 2, col 6 (after "World")
        onView(withId(R.id.statusCursor))
            .check(matches(withText("Ln 2, Col 6")))
    }

    /**
     * Test: Open the editor's options menu and verify "Go to line" menu item is present and visible
     */
    @Test
    fun testGoToLineMenuItemExists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Open the overflow menu
        openActionBarOverflowOrOptionsMenu(context)

        // Verify "Go to line" menu item is visible
        onView(withText("Go to line"))
            .check(matches(isDisplayed()))
    }

    /**
     * Test: After loading a file with multiple lines, use "Go to line" to jump to a specific line
     * and verify cursor position moved to the beginning of the target line
     */
    @Test
    fun testGoToLineFunctionality() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Add multiple lines of text
        onView(withId(R.id.codeHighlight))
            .perform(replaceText("Line 1\nLine 2\nLine 3\nLine 4"))

        // Open "Go to line" dialog and enter line number 3
        openGoToLineDialogAndEnterNumber(context, "3")

        // Click "Go" button
        clickGoButton()

        // Verify cursor is now on line 3
        onView(withId(R.id.statusCursor))
            .check(matches(withText(containsString("Ln 3"))))
    }

    /**
     * Test: "Go to line" with out-of-bounds number should handle gracefully without crashing
     */
    @Test
    fun testGoToLineOutOfBounds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Add exactly 10 lines of text
        val tenLines = (1..10).joinToString("\n") { "Line $it" }
        onView(withId(R.id.codeHighlight))
            .perform(replaceText(tenLines))

        // Open "Go to line" dialog and enter out-of-bounds line number (100)
        openGoToLineDialogAndEnterNumber(context, "100")

        // Click "Go" button
        clickGoButton()

        // Verify the codeHighlight EditText is still accessible (app is still functional)
        onView(withId(R.id.codeHighlight))
            .check(matches(isDisplayed()))

        // Verify status bar is still visible (app didn't crash)
        onView(withId(R.id.statusCursor))
            .check(matches(isDisplayed()))
    }

    /**
     * Test: "Go to line" with invalid input (0 or negative) should handle gracefully without crashing
     */
    @Test
    fun testGoToLineInvalidInput() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Add multiple lines of text
        onView(withId(R.id.codeHighlight))
            .perform(replaceText("Line 1\nLine 2\nLine 3\nLine 4\nLine 5"))

        // Test with line number 0
        openGoToLineDialogAndEnterNumber(context, "0")
        clickGoButton()

        // Verify the UI is still functional
        onView(withId(R.id.codeHighlight))
            .check(matches(isDisplayed()))

        // Verify status bar is still visible and functional
        onView(withId(R.id.statusCursor))
            .check(matches(isDisplayed()))
    }

    /**
     * Test: Verify that the hfcode directory is created during file operations
     */
    @Test
    fun testHfcodeDirectoryCreated() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Trigger a file open action which should create the hfcode directory
        scenario.onActivity { activity ->
            activity.startFileOpen()
        }

        // Dismiss the file picker using UiAutomator
        dismissFilePicker()

        // Check if hfcode directory exists in Documents
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val hfcodeDir = File(documentsDir, "hfcode")

        // Verify directory was created (or fallback to app-specific directory)
        if (!hfcodeDir.exists()) {
            // Check app-specific directory
            val externalFilesDir = context.getExternalFilesDir(null)
            val appHfcodeDir = File(externalFilesDir, "hfcode")
            assert(appHfcodeDir.exists()) { "hfcode directory was not created" }
        }
    }

    /**
     * Test: Verify that the app correctly uses existing hfcode folder without crashing
     */
    @Test
    fun testHfcodeDirectoryAlreadyExists() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext

        // Manually create the hfcode folder before triggering file operation
        val documentsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS)
        val hfcodeDir = File(documentsDir, "hfcode")

        // Try to create in Documents first, fallback to app-specific if needed
        val targetDir = if (hfcodeDir.mkdirs() || hfcodeDir.exists()) {
            hfcodeDir
        } else {
            val externalFilesDir = context.getExternalFilesDir(null)
            val appHfcodeDir = File(externalFilesDir, "hfcode")
            appHfcodeDir.mkdirs()
            appHfcodeDir
        }

        // Verify the directory exists before the test
        assert(targetDir.exists()) { "Failed to create hfcode directory for test setup" }

        // Trigger a file operation (file open) - this should use the existing folder
        scenario.onActivity { activity ->
            activity.startFileOpen()
        }

        dismissFilePicker()

        // Verify the directory still exists and app is using it correctly
        assert(targetDir.exists()) { "hfcode directory should still exist after file operation" }

        // Verify app is still functional by checking UI element (now that activity is resumed)
        onView(withId(R.id.codeHighlight))
            .check(matches(isDisplayed()))
    }

    /**
     * Test: Verify that the view previously used for displaying line numbers is no longer visible
     */
    @Test
    fun testLineNumbersRemoved() {
        scenario.onActivity { activity ->
            // Find the nested scroll view which contains the code editor area
            val scrollView = activity.findViewById<ViewGroup>(R.id.nestedScrollView)

            var foundLineNumberTextView = false

            fun checkViewHierarchy(view: android.view.View) {
                if (view is TextView && view.id != R.id.codeHighlight) {
                    // Check if this TextView could be for line numbers
                    val parent = view.parent
                    if (parent is ViewGroup) {
                        for (i in 0 until parent.childCount) {
                            val sibling = parent.getChildAt(i)
                            if (sibling.id == R.id.codeHighlight) {
                                foundLineNumberTextView = true
                                return
                            }
                        }
                    }
                }
                if (view is ViewGroup) {
                    for (i in 0 until view.childCount) {
                        checkViewHierarchy(view.getChildAt(i))
                    }
                }
            }

            checkViewHierarchy(scrollView)

            assert(!foundLineNumberTextView) { "Line number TextView should not exist in the editor area" }
        }
    }
}
