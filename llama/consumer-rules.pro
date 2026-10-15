# JNI entry points and callback interfaces are resolved by name from native code.
-keep class app.bytebrief.llama.LlamaNative { native <methods>; }
-keep interface app.bytebrief.llama.LlamaNative$PieceListener { *; }
-keep class app.bytebrief.llama.LlamaException { *; }
