from pathlib import Path
import argparse
from onnxruntime.quantization import QuantType, quantize_dynamic


def quantize_model(model_path: str, quantized_path: str) -> None:
    source = Path(model_path).resolve()
    target = Path(quantized_path).resolve()
    if source.suffix.lower() != ".onnx" or target.suffix.lower() != ".onnx":
        raise ValueError("PULSE CORE quantization currently requires ONNX files")
    target.parent.mkdir(parents=True, exist_ok=True)
    quantize_dynamic(str(source), str(target), weight_type=QuantType.QInt8)


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("model_path")
    parser.add_argument("quantized_path")
    args = parser.parse_args()
    quantize_model(args.model_path, args.quantized_path)
