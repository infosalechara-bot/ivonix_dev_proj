# PULSE CORE — COR-023

Internal inference runtime for PULSE Machine Connect.

## Current capabilities

- ONNX Runtime execution
- ORT graph optimization (`ORT_ENABLE_ALL`)
- CPU multi-threaded execution
- CUDA provider selection when the runtime image has a compatible provider installed
- Safe model-root path resolution
- Supabase-backed inference job lifecycle
- INT8 dynamic quantization utility for ONNX models
- Health endpoint

## Runtime contract

`POST /infer` accepts `job_id`, `model_id`, and `input_data.data`. The runtime loads the registered model metadata from Supabase, executes it, and records status, output, and latency in `inference_jobs`.

Models must be present under `CORE_MODEL_ROOT` (default `/models`). The runtime intentionally rejects paths outside that directory.

## Important boundary

This is an inference engine, not a claim of hardware-equivalent GPU performance. TensorRT, true request batching, memory-pool tuning, pruning, and JIT compilation are follow-on optimization layers. CUDA execution also requires an image/runtime with the matching ONNX Runtime GPU dependencies and compatible host drivers.
