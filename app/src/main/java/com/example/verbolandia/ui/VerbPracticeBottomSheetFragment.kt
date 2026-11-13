package com.example.verbolandia.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.activityViewModels
import com.example.verbolandia.R
import com.example.verbolandia.databinding.BottomSheetVerbPracticeBinding
import com.example.verbolandia.model.Verb
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButton

class VerbPracticeBottomSheetFragment : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_VERB = "arg_verb"

        fun newInstance(verb: Verb): VerbPracticeBottomSheetFragment {
            return VerbPracticeBottomSheetFragment().apply {
                arguments = Bundle().apply {
                    putSerializable(ARG_VERB, verb)
                }
            }
        }
    }

    private var _binding: BottomSheetVerbPracticeBinding? = null
    private val binding get() = _binding!!

    private val viewModel: SharedViewModel by activityViewModels()
    private var verb: Verb? = null

    private var currentStep = 1
    private var userErrors = 0
    private var currentCorrectAnswer: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        verb = arguments?.getSerializable(ARG_VERB) as? Verb
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetVerbPracticeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val activeVerb = verb ?: run {
            dismiss()
            return
        }

        binding.tvSheetTitle.text = getString(R.string.practice_sheet_title, activeVerb.base.uppercase())
        binding.btnCloseSheet.setOnClickListener { dismiss() }
        binding.btnNextStep.setOnClickListener {
            currentStep++
            if (currentStep <= 3) {
                renderStep(currentStep, activeVerb)
            } else {
                finishPractice(activeVerb)
            }
        }

        binding.btnFinishPractice.setOnClickListener {
            dismiss()
        }

        renderStep(currentStep, activeVerb)
    }

    private fun renderStep(step: Int, v: Verb) {
        binding.layoutFeedback.visibility = View.GONE
        binding.practiceProgress.progress = step

        resetOptionButton(binding.btnOption1)
        resetOptionButton(binding.btnOption2)
        resetOptionButton(binding.btnOption3)
        binding.btnOption3.visibility = View.VISIBLE

        when (step) {
            1 -> setupStep1(v)
            2 -> setupStep2(v)
            3 -> setupStep3(v)
        }
    }

    private fun setupStep1(v: Verb) {
        binding.tvStepIndicator.text = getString(R.string.practice_step_1_title)
        binding.tvExercisePrompt.text = getString(R.string.practice_step_1_prompt, v.base)
        binding.tvContextSentence.visibility = View.GONE

        currentCorrectAnswer = "${v.past} · ${v.pastParticiple}"

        val distractor1 = if (v.isIrregular) {
            val reg = if (v.base.endsWith("e")) "${v.base}d" else "${v.base}ed"
            "$reg · $reg"
        } else {
            "${v.base} · ${v.gerund}"
        }

        val distractor2 = if (v.past != v.pastParticiple) {
            "${v.pastParticiple} · ${v.past}"
        } else {
            "${v.thirdPerson} · ${v.gerund}"
        }

        val options = listOf(currentCorrectAnswer, distractor1, distractor2).distinct().shuffled()
        bindOptions(options)
    }

    private fun setupStep2(v: Verb) {
        binding.tvStepIndicator.text = getString(R.string.practice_step_2_title)
        binding.tvExercisePrompt.text = getString(R.string.practice_step_2_prompt)
        binding.tvContextSentence.visibility = View.VISIBLE
        binding.tvContextSentence.text = getString(R.string.practice_step_2_sentence_format)

        currentCorrectAnswer = v.past

        val distractor1 = v.base
        val distractor2 = if (v.pastParticiple != v.past) v.pastParticiple else v.gerund

        val options = listOf(currentCorrectAnswer, distractor1, distractor2).distinct().shuffled()
        bindOptions(options)
    }

    private fun setupStep3(v: Verb) {
        binding.tvStepIndicator.text = getString(R.string.practice_step_3_title)
        binding.tvExercisePrompt.text = getString(R.string.practice_step_3_prompt)
        binding.tvContextSentence.visibility = View.GONE

        currentCorrectAnswer = "They have ${v.pastParticiple} everything."
        val wrongSentence = if (v.isIrregular) {
            "They have ${v.past} everything."
        } else {
            "They didn't ${v.past} anything."
        }

        binding.btnOption3.visibility = View.GONE
        val options = listOf(currentCorrectAnswer, wrongSentence).shuffled()
        bindOptions(options)
    }

    private fun bindOptions(options: List<String>) {
        val buttons = listOf(binding.btnOption1, binding.btnOption2, binding.btnOption3)
        for (i in buttons.indices) {
            if (i < options.size) {
                buttons[i].visibility = View.VISIBLE
                buttons[i].text = options[i]
                buttons[i].setOnClickListener { onOptionSelected(buttons[i], options[i]) }
            } else {
                buttons[i].visibility = View.GONE
            }
        }
    }

    private fun onOptionSelected(selectedButton: MaterialButton, selectedAnswer: String) {
        val buttons = listOf(binding.btnOption1, binding.btnOption2, binding.btnOption3)
        buttons.forEach { it.isEnabled = false }

        val isCorrect = selectedAnswer == currentCorrectAnswer
        if (isCorrect) {
            selectedButton.strokeColor = android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.success)
            )
            binding.tvFeedbackMessage.text = getString(R.string.practice_feedback_correct)
            binding.tvFeedbackMessage.setTextColor(requireContext().getColor(R.color.success))
        } else {
            userErrors++
            selectedButton.strokeColor = android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.error)
            )
            // Highlight the correct one
            buttons.find { it.text == currentCorrectAnswer }?.strokeColor = android.content.res.ColorStateList.valueOf(
                requireContext().getColor(R.color.success)
            )
            binding.tvFeedbackMessage.text = getString(R.string.practice_feedback_incorrect, currentCorrectAnswer)
            binding.tvFeedbackMessage.setTextColor(requireContext().getColor(R.color.error))
        }

        binding.layoutFeedback.visibility = View.VISIBLE
        if (currentStep == 3) {
            binding.btnNextStep.text = getString(R.string.practice_btn_finish)
        } else {
            binding.btnNextStep.text = getString(R.string.practice_btn_continue)
        }
    }

    private fun resetOptionButton(button: MaterialButton) {
        button.isEnabled = true
        button.strokeColor = null
    }

    private fun finishPractice(v: Verb) {
        val isSuccessful = userErrors == 0
        viewModel.recordPracticeAttempt(v.base, isSuccessful, "focused_micro_session")

        binding.cardQuestionContainer.visibility = View.GONE
        binding.practiceProgress.visibility = View.GONE
        binding.layoutSuccess.visibility = View.VISIBLE
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
