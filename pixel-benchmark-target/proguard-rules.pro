# Benchmark consumer keeps only its launcher Activity name; SDK code remains eligible for R8 optimization.
-keep class com.purride.pixelbenchmark.target.PixelBenchmarkActivity { <init>(); }
