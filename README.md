# 📱 MobiRAG

**MobiRAG** is a fully on-device mobile app that lets you **chat with any PDF stored on your phone** using a compact LLM + embedding model — all without internet or cloud access. It’s fast, private, and works entirely offline.

---

## 🎥 Demo
<div align="center">

https://github.com/user-attachments/assets/f332debd-9a12-4c38-81a4-b992d62ee6b4

</div>

▶️ [YT Video](https://youtube.com/shorts/8FJI6Fewlgc?feature=share)

---

## ✨ Features

| Feature                         | Description |
|----------------------------------|-------------|
| 🔐 100% On-Device                | No cloud calls. No telemetry. Your data never leaves your phone. |
| 🧠 Embeddings via ONNX          | Runs `all-MiniLM-L6-v2` model for fast, high-quality sentence embeddings. |
| 📚 PDF Discovery & Parsing      | Detects and processes all PDFs on device using `PDFBox`. |
| 🔎 Semantic Search with FAISS   | PQ-compressed embeddings enable scalable vector search on-device. |
| 💬 LLM Chat with Context        | On-device LLM like Qwen 0.5B generates answers grounded in PDF context. |
| 🔁 Hybrid RAG                   | Combines FAISS vector similarity with TF-IDF keyword overlap. |
| 🖼️ Lightweight UI              | Responsive and optimized for phones with minimalistic design. |

---

## 🚀 How it Works

1. **PDF Indexing**
   - Extracts text using PDFBox
   - Splits into sentence-based chunks
   - Generates ONNX-based embeddings
   - Compresses using FAISS + Product Quantization

2. **Query Execution**
   - Embeds query with ONNX model
   - Searches top-k chunks via FAISS
   - Boosts scores with keyword matching (TF-IDF)
   - Builds prompt with context
   - Streams response from on-device LLM (via `llama.cpp`)

---

## 🛠️ Getting Started

### 🔧 Setup

```bash
git clone https://github.com/nishchaljs/MobiRAG.git
cd MobiRAG
git submodule update --init --recursive
```
---

### 📲 Build
1. Open in Android Studio

2. Add your ONNX model and tokenizer to ```assets/all-minilm-l6-v2/```

3. Place your *.gguf quantized LLM file into ```Android/data/com.mobirag/files/```

### 📦 Dependencies
1. FAISS
2. llama.cpp
3. PDFBox Android
4. ONNX Runtime

## References:
🔗 Sentence-Embeddings-Android — ONNX + tokenizer-based embedding pipeline
🔗 android-faiss — Adapting FAISS for mobile vector search
🔗 llama.cpp — Blazing-fast LLM inference engine for on-device usage

## 📜 License
MIT License
