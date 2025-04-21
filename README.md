# 📱 MobiRAG

**MobiRAG** (Mobile Retrieval-Augmented Generation) is a lightweight, privacy-first Android app that enables users to **chat with any PDF file stored on their phone** — entirely offline. With on-device embedding generation, vector compression, and SLM inference, MobiRAG brings the power of AI search and summarization directly to your pocket.

No internet, no cloud servers, and no telemetry — everything runs natively on your phone, ensuring complete data peivacy and zero leakage. Whether you’re reviewing research papers, legal documents, or ebooks, MobiRAG offers a seamless way to search, ask questions, and summarize content using optimized RAG for mobile devices.

---

## 🎥 Demo
<div align="center">

https://github.com/user-attachments/assets/f332debd-9a12-4c38-81a4-b992d62ee6b4

</div>

🔺️ [YT Video](https://youtube.com/shorts/8FJI6Fewlgc?feature=share)

---

## ✨ Features

| Feature                         | Description |
|----------------------------------|-------------|
| 🔐 100% On-Device                | No cloud calls. No telemetry. Your data never leaves your phone. |
| 🧠 Embeddings via ONNX          | Runs `all-MiniLM-L6-v2` model for fast, good-quality sentence embeddings on phone. |
| 📚 PDF Discovery & Parsing      | Detects and processes all PDFs on device using `PDFBox`. |
| 🔎 Semantic Search with FAISS   | PQ-compressed embeddings enable scalable vector search on-device. |
| 💬 SLM Chat with Context        | On-device Small LM like Qwen 0.5B generates answers grounded in PDF context. |
| 🔁 Hybrid RAG                   | Combines FAISS vector similarity with TF-IDF keyword overlap. |
| 🖼️ Lightweight UI              | Responsive and optimized for phones with minimalistic design. |

---

## 🚀 How it Works

### 1. PDF Indexing
- Extracts text from each page using PDFBox
- Splits into clean sentence-based units
- Combines sentences into chunks of ~1024 characters with 1-sentence overlap
- Encodes all chunks using ONNX `all-MiniLM-L6-v2` model
- Compresses embeddings using **FAISS Product Quantization (PQ)**
- Stores only metadata (PDF URI, page, offset), not the chunk text itself

### 2. Query Execution
- User query is embedded using ONNX `all-MiniLM-L6-v2` model
- FAISS returns top-k chunk IDs based on PQ-compressed vector similarity (nearest neighbours appraoch)
- Matching metadata is used to extract chunk text on the fly
- TF-IDF keyword overlap further refines relevance
- Combined context is used to prompt a local SLM (Qwen) to generate the answer

> This reduces memory footprint and ensures that deleted PDFs cannot be queried later (privacy by design).

---

## 🛠️ Technical Highlights

- ✨ **Efficient Compression**: FAISS PQ compresses 384-dim vectors at **32x**, enabling 1000 PDFs to fit in ~2.4MB (upto 97x can be obtained with a compromize on performance).
- ✨ **FAISS Tradeoff**: Slight drop in search quality from PQ vs flat index — but acceptable for mobile efficiency.
- ✨ **Metadata-Only Storage**: Chunks are not stored — only chunkID + PDF location + offset are. Text is extracted on-demand.
- ✨ **Privacy by Design**: If a PDF is deleted from storage, its chunks become inaccessible.

---

## 🚨 Requirements

- Android 8.0+
- ARM64 device (with >=4GB RAM recommended)
- LLM file: `qwen2.5-0.5b-instruct-q4_k_m.gguf`

---

## 📆 Getting Started

### 🔧 Setup
```bash
git clone https://github.com/nishchaljs/MobiRAG.git
cd MobiRAG
git submodule update --init --recursive
```

### 📲 Build Instructions
1. Open in Android Studio
2. Add your embedding ONNX + tokenizer to `assets/all-minilm-l6-v2/`
3. Place your `.gguf` LLM file inside `Android/data/com.mobirag/files/`

### 📦 Key Dependencies
- [FAISS](https://github.com/facebookresearch/faiss)
- [llama.cpp](https://github.com/ggml-org/llama.cpp)
- [PDFBox Android](https://github.com/TomRoush/PdfBox-Android)
- [ONNX Runtime](https://onnxruntime.ai/)

---

## ✅ TODO

- [ ] **Refactor to MVC architecture**  
  Reorganize app code into clean `Model-View-Controller` separation for maintainability and testing

- [ ] **Improve SLM Inference speed**  
  ```llama.cpp``` uses android cpu - tends to be very slow for long prompts. Need to explore alternatives like ```mlc llm``` to utilize GPUs for inference on android

- [ ] **Improve hybrid RAG scoring**  
  Replace naive combination of vector similarity + keyword overlap with a unified scoring function (e.g., z-score normalization, weighted distances)

- [ ] **Optimize FAISS PQ & SLM inference**  
  Experiment with different PQ training sizes, nprobe settings, and SLM decoding strategies for best quality-performance tradeoff

- [ ] **Improve system prompt**  
  Design a robust, guardrailed prompt template that guides the SLM to avoid hallucinations and respect query constraints

---

## ⚡ Credits

- 🔗 [Sentence-Embeddings-Android](https://github.com/shubham0204/Sentence-Embeddings-Android)
- 🔗 [android-faiss](https://github.com/luojinlongjjj/android-faiss)

---

## 📄 License

This project is licensed under the [MIT License](LICENSE).

