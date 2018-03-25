package me.tylerbwong.stack.presentation.questions.detail

import android.app.ActivityOptions
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.support.v7.app.AppCompatActivity
import com.bumptech.glide.request.RequestOptions
import kotlinx.android.synthetic.main.activity_question_detail.*
import kotlinx.android.synthetic.main.question_holder.*
import me.tylerbwong.stack.R
import me.tylerbwong.stack.data.model.Answer
import me.tylerbwong.stack.data.model.Question
import me.tylerbwong.stack.data.model.User
import me.tylerbwong.stack.presentation.utils.CustomTabsLinkResolver
import me.tylerbwong.stack.presentation.utils.GlideApp
import me.tylerbwong.stack.presentation.utils.format
import me.tylerbwong.stack.presentation.utils.toHtml
import ru.noties.markwon.Markwon
import ru.noties.markwon.SpannableConfiguration

class QuestionDetailActivity : AppCompatActivity(), QuestionDetailContract.View {

    private val presenter = QuestionDetailPresenter(this)

    private lateinit var question: Question
    private lateinit var owner: User

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_question_detail)

        setSupportActionBar(toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = ""

        questionTitle.text = intent.getStringExtra(QUESTION_TITLE).toHtml()
        questionBody.text = intent.getStringExtra(QUESTION_BODY).toHtml()
        presenter.questionId = intent.getIntExtra(QUESTION_ID, 0).also {
            rootQuestionView.transitionName = it.toString()
        }
        owner = intent.getParcelableExtra(QUESTION_OWNER)

        username.text = owner.displayName.toHtml()
        GlideApp.with(this)
                .load(owner.profileImage)
                .placeholder(R.drawable.user_image_placeholder)
                .apply(RequestOptions.circleCropTransform())
                .into(userImage)
        badgeView.badgeCounts = owner.badgeCounts
        reputation.text = owner.reputation.toLong().format()

        presenter.subscribe()
    }

    override fun onStop() {
        super.onStop()
        presenter.unsubscribe()
    }

    override fun setQuestion(question: Question) {
        this.question = question
        questionBody.maxLines = Integer.MAX_VALUE
        questionBody.ellipsize = null

        question.bodyMarkdown?.let {
            Markwon.setMarkdown(
                    questionBody,
                    SpannableConfiguration.builder(this)
                            .linkResolver(CustomTabsLinkResolver())
                            .build(),
                    it
            )
        }
    }

    override fun setAnswers(answers: List<Answer>) {
    }

    override fun setRefreshing(isRefreshing: Boolean) {
    }

    companion object {
        private const val QUESTION_ID = "id"
        private const val QUESTION_TITLE = "title"
        private const val QUESTION_BODY = "body"
        private const val QUESTION_OWNER = "owner"

        fun startActivity(
                context: Context,
                activityOptions: ActivityOptions,
                id: Int,
                title: String,
                body: String?,
                owner: User
        ) {
            val intent = Intent(context, QuestionDetailActivity::class.java).apply {
                putExtra(QUESTION_ID, id)
                putExtra(QUESTION_TITLE, title)
                putExtra(QUESTION_BODY, body)
                putExtra(QUESTION_OWNER, owner)
            }
            context.startActivity(intent, activityOptions.toBundle())
        }
    }
}