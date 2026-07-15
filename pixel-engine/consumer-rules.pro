# PixelFrameScheduler.Default 通过类名反射加载 Android Choreographer 实现；消费者混淆时必须保留其名称与成员。
-keep class com.purride.pixelui.host.ChoreographerFrameScheduler { *; }

# pixel-runtime 通过固定类名读取可选 widgets 主题桥，聚合/R8 消费者必须保留该 internal 入口。
-keep class com.purride.pixelui.internal.PixelWidgetArtifactAccess { *; }
