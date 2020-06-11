package me.tylerbwong.stack.ui.questions.detail.post

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.core.widget.NestedScrollView.OnScrollChangeListener
import androidx.fragment.app.activityViewModels
import androidx.fragment.app.viewModels
import androidx.lifecycle.observe
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.tabs.TabLayout.OnTabSelectedListener
import com.google.android.material.tabs.TabLayout.Tab
import dev.chrisbanes.insetter.Insetter
import me.saket.bettermovementmethod.BetterLinkMovementMethod
import me.tylerbwong.stack.BuildConfig
import me.tylerbwong.stack.R
import me.tylerbwong.stack.databinding.PostAnswerFragmentBinding
import me.tylerbwong.stack.ui.ApplicationWrapper
import me.tylerbwong.stack.ui.BaseFragment
import me.tylerbwong.stack.ui.questions.detail.QuestionDetailActivity
import me.tylerbwong.stack.ui.questions.detail.QuestionDetailMainViewModel
import me.tylerbwong.stack.ui.questions.detail.QuestionDetailMainViewModelFactory
import me.tylerbwong.stack.ui.utils.hideKeyboard
import me.tylerbwong.stack.ui.utils.markdown.setMarkdown
import me.tylerbwong.stack.ui.utils.setThrottledOnClickListener
import me.tylerbwong.stack.ui.utils.showKeyboard
import me.tylerbwong.stack.ui.utils.showSnackbar
import javax.inject.Inject

class PostAnswerFragment : BaseFragment<PostAnswerFragmentBinding>(
    PostAnswerFragmentBinding::inflate
) {

    @Inject
    lateinit var viewModelFactory: PostAnswerViewModelFactory

    @Inject
    lateinit var mainViewModelFactory: QuestionDetailMainViewModelFactory

    private val viewModel by viewModels<PostAnswerViewModel> { viewModelFactory }
    private val mainViewModel by activityViewModels<QuestionDetailMainViewModel> { mainViewModelFactory }
    private var menu: Menu? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ApplicationWrapper.stackComponent.inject(this)
        setHasOptionsMenu(true)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        mainViewModel.clearFields.observe(viewLifecycleOwner) { clearFields() }

        viewModel.questionId = arguments?.getInt(QuestionDetailActivity.QUESTION_ID, 0) ?: 0
        mainViewModel.liveQuestion.observe(viewLifecycleOwner) {
            viewModel.questionTitle = it.title
        }

        viewModel.snackbar.observe(viewLifecycleOwner) {
            val activity = activity as? QuestionDetailActivity

            when (it) {
                is PostAnswerState.Success -> {
                    clearFields()
                    activity?.toggleAnswerMode(isInAnswerMode = false)
                    binding.rootLayout.showSnackbar(it.messageId, duration = it.duration)
                }
                is PostAnswerState.Loading, is PostAnswerState.Error -> {
                    togglePostAnswerButtonVisibility(isVisible = it is PostAnswerState.Error)
                    binding.scrollView.showSnackbar(it.messageId, duration = it.duration)
                }
            }
        }

        viewModel.savedDraft.observe(viewLifecycleOwner) {
            binding.markdownEditText.setText(it)
        }

        binding.debugPreview.isVisible = BuildConfig.DEBUG

        binding.previewText.apply {
            setTextIsSelectable(true)
            movementMethod = BetterLinkMovementMethod.getInstance()
        }

        binding.scrollView.setOnScrollChangeListener(OnScrollChangeListener { _, _, scrollY, _, oldScrollY ->
            if (scrollY > oldScrollY) {
                binding.postAnswerButton.shrink()
            } else {
                binding.postAnswerButton.extend()
            }
        })

        binding.tabLayout.addOnTabSelectedListener(object : OnTabSelectedListener {
            override fun onTabReselected(tab: Tab) {
                // no-op
            }

            override fun onTabUnselected(tab: Tab) {
                // no-op
            }

            override fun onTabSelected(tab: Tab) {
                onTabChanged(tab.position)
            }
        })

        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(viewModel.selectedTabPosition))

        binding.postAnswerButton.setThrottledOnClickListener {
            if (!binding.markdownEditText.text.isNullOrBlank()) {
                viewModel.postAnswer(
                    binding.markdownEditText.text.toString(),
                    isPreview = BuildConfig.DEBUG
                )
            }
        }

        Insetter.builder().setOnApplyInsetsListener { buttonView, insets, initialState ->
            (buttonView.layoutParams as? ViewGroup.MarginLayoutParams)?.let {
                buttonView.layoutParams = it.apply {
                    setMargins(
                        leftMargin,
                        topMargin,
                        rightMargin,
                        initialState.margins.bottom + insets.systemWindowInsetBottom
                    )
                }
            }
        }.applyToView(binding.postAnswerButton)

        viewModel.fetchDraftIfExists()
    }

    override fun onResume() {
        super.onResume()
        togglePostAnswerButtonVisibility()
    }

    override fun onViewStateRestored(savedInstanceState: Bundle?) {
        super.onViewStateRestored(savedInstanceState)
        tearDownTextWatcher()
        setUpTextWatcher()
        refreshPreview()
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        this.menu = menu
        inflater.inflate(R.menu.menu_post, menu)
        toggleMenuVisibility(isVisible = !binding.markdownEditText.text.isNullOrBlank())
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.save_draft -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.default_dialog_bg))
                    .setTitle(R.string.save_draft)
                    .setPositiveButton(R.string.save_draft) { _, _ ->
                        viewModel.saveDraft(binding.markdownEditText.text.toString())
                        Toast.makeText(
                            requireContext(),
                            R.string.draft_saved,
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
                    .create()
                    .show()
            }
            R.id.discard -> {
                MaterialAlertDialogBuilder(requireContext())
                    .setBackground(ContextCompat.getDrawable(requireContext(), R.drawable.default_dialog_bg))
                    .setTitle(R.string.discard_answer)
                    .setPositiveButton(R.string.discard) { _, _ ->
                        clearFields()
                        viewModel.deleteDraft()
                    }
                    .setNegativeButton(R.string.cancel) { dialog, _ -> dialog.cancel() }
                    .create()
                    .show()
            }
        }
        return super.onOptionsItemSelected(item)
    }

    private fun toggleMenuVisibility(isVisible: Boolean) {
        listOf(R.id.save_draft, R.id.discard).forEach {
            menu?.findItem(it)?.isVisible = isVisible
        }
    }

    private fun onTabChanged(position: Int) {
        viewModel.selectedTabPosition = position
        when (position) {
            0 -> {
                binding.markdownInputLayout.isVisible = true
                binding.previewText.isGone = true
                binding.markdownEditText.requestFocus()
                binding.markdownEditText.showKeyboard()
            }
            1 -> {
                binding.markdownInputLayout.isGone = true
                binding.previewText.apply {
                    isVisible = true
                    hideKeyboard()
                    refreshPreview()
                }
            }
        }
    }

    private fun refreshPreview() {
        binding.previewText.apply {
            if (!binding.markdownEditText.text.isNullOrBlank()) {
                gravity = Gravity.START or Gravity.TOP
                setMarkdown(binding.markdownEditText.text.toString())
            } else {
                gravity = Gravity.CENTER
                setText(R.string.no_preview)
            }
        }
    }

    private fun togglePostAnswerButtonVisibility(
        isVisible: Boolean = !binding.markdownEditText.text.isNullOrBlank()
    ) = if (isVisible) {
        binding.postAnswerButton.show()
        binding.postAnswerButton.extend()
    } else {
        binding.postAnswerButton.hide()
    }

    private fun clearFields() {
        tearDownTextWatcher()
        mainViewModel.hasContent = false
        toggleMenuVisibility(isVisible = false)
        binding.markdownEditText.text = null
        binding.previewText.text = null
        setUpTextWatcher()
        togglePostAnswerButtonVisibility(isVisible = false)
        binding.debugPreview.isChecked = BuildConfig.DEBUG
        binding.tabLayout.selectTab(binding.tabLayout.getTabAt(0))
    }

    private fun tearDownTextWatcher() {
        // Fix TextInputLayout error message crash
        if (viewModel.markdownTextWatcher != null) {
            binding.markdownEditText.removeTextChangedListener(viewModel.markdownTextWatcher)
            viewModel.markdownTextWatcher = null
        }
    }

    private fun setUpTextWatcher() {
        viewModel.markdownTextWatcher = object : TextWatcher {
            override fun afterTextChanged(text: Editable) {
                val hasContent = text.isNotBlank()
                mainViewModel.hasContent = hasContent
                toggleMenuVisibility(isVisible = hasContent)
                togglePostAnswerButtonVisibility(isVisible = hasContent)
            }

            override fun beforeTextChanged(text: CharSequence, start: Int, count: Int, after: Int) {
                // no-op
            }

            override fun onTextChanged(text: CharSequence, start: Int, before: Int, count: Int) {
                binding.markdownInputLayout.error = if (text.isBlank()) {
                    getString(R.string.answer_error)
                } else {
                    null
                }
            }
        }
        binding.markdownEditText.addTextChangedListener(viewModel.markdownTextWatcher)
    }

    companion object {
        fun newInstance(id: Int): PostAnswerFragment {
            return PostAnswerFragment().apply {
                arguments = Bundle().apply {
                    putInt(QuestionDetailActivity.QUESTION_ID, id)
                }
            }
        }
    }
}
