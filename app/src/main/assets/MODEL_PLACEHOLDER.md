# Gemma Model Placeholder
#
# Place the Gemma 4 E2B .task file here with the name:
#   gemma.task
#
# The GemmaAnalyzer loads this file via MediaPipe LLM Inference API:
#   LlmInferenceOptions.builder().setModelAssetPath("gemma.task")
#
# The model file is intentionally excluded from version control (.gitignore)
# because it can be several GB in size.
#
# To obtain the model:
#   1. Download from: https://www.kaggle.com/models/google/gemma
#   2. Rename to gemma.task
#   3. Copy to this directory: app/src/main/assets/
