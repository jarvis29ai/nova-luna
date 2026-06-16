package com.nova.luna.diagnostics

enum class NativeProofStage {
    LOAD_ONLY,
    CREATE_CONTEXT_ONLY,
    TOKENIZE_ONLY,
    CONTROLLED_HANG,
    DECODE_ONE_PROMPT_TOKEN,
    GENERATE_ONE_TOKEN,
    READABLE_OUTPUT,
    STRUCTURED_JSON_OUTPUT
}

object NativeProofContract {
    const val SERVICE_CLASS_NAME = "com.nova.luna.diagnostics.NativeInferenceProofProcessService"

    const val MSG_RUN_STAGE = 1
    const val MSG_RESULT = 2

    const val EXTRA_STAGE = "native_proof_stage"
    const val EXTRA_MODEL_ID = "native_proof_model_id"
    const val EXTRA_MODEL_PATH = "native_proof_model_path"
    const val EXTRA_MODEL_SHA256 = "native_proof_model_sha256"
    const val EXTRA_PROMPT = "native_proof_prompt"
    const val EXTRA_MAX_TOKENS = "native_proof_max_tokens"
    const val EXTRA_TIMEOUT_MS = "native_proof_timeout_ms"
    const val EXTRA_CONTROLLER_PID = "native_proof_controller_pid"

    const val RESULT_STAGE = "native_proof_result_stage"
    const val RESULT_STAGE_REACHED = "native_proof_result_stage_reached"
    const val RESULT_SUCCESS = "native_proof_result_success"
    const val RESULT_MESSAGE = "native_proof_result_message"
    const val RESULT_MODEL_PATH = "native_proof_result_model_path"
    const val RESULT_MODEL_SHA256 = "native_proof_result_model_sha256"
    const val RESULT_MODEL_ARCH = "native_proof_result_model_arch"
    const val RESULT_CONTEXT_SIZE = "native_proof_result_context_size"
    const val RESULT_BATCH_SIZE = "native_proof_result_batch_size"
    const val RESULT_THREAD_COUNT = "native_proof_result_thread_count"
    const val RESULT_VOCAB_SIZE = "native_proof_result_vocab_size"
    const val RESULT_LOAD_MS = "native_proof_result_load_ms"
    const val RESULT_PROMPT_EVAL_MS = "native_proof_result_prompt_eval_ms"
    const val RESULT_GENERATION_MS = "native_proof_result_generation_ms"
    const val RESULT_TOKENS_PER_SECOND = "native_proof_result_tokens_per_second"
    const val RESULT_TOKENS_GENERATED = "native_proof_result_tokens_generated"
    const val RESULT_PROMPT_TOKEN_IDS = "native_proof_result_prompt_token_ids"
    const val RESULT_GENERATED_TOKEN_IDS = "native_proof_result_generated_token_ids"
    const val RESULT_DECODED_TEXT = "native_proof_result_decoded_text"
    const val RESULT_REAL_FORWARD_PASS = "native_proof_result_real_forward_pass"
    const val RESULT_NATIVE_FORWARD_PASS_COUNT = "native_proof_result_native_forward_pass_count"
    const val RESULT_LOGITS_COMPUTED = "native_proof_result_logits_computed"
    const val RESULT_LOGITS_FINITE = "native_proof_result_logits_finite"
    const val RESULT_LOGITS_PREVIEW = "native_proof_result_logits_preview"
    const val RESULT_SAMPLED_FROM_MODEL_LOGITS = "native_proof_result_sampled_from_model_logits"
    const val RESULT_SIMULATION = "native_proof_result_simulation"
    const val RESULT_SERVICE_PID = "native_proof_result_service_pid"
    const val RESULT_CONTROLLER_PID = "native_proof_result_controller_pid"
    const val RESULT_START_MS = "native_proof_result_start_ms"
    const val RESULT_END_MS = "native_proof_result_end_ms"
    const val RESULT_DIAGNOSTICS = "native_proof_result_diagnostics"
    const val RESULT_ERROR = "native_proof_result_error"
}
