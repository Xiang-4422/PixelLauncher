package com.purride.pixelui

import com.purride.pixelui.state.PixelListController
import com.purride.pixelui.state.PixelPagerController
import com.purride.pixelui.state.PixelTextFieldController

/**
 * Flutter 风格控制器公开别名。
 *
 * 当前阶段先统一对外命名，
 * 内部仍然复用已有 `Pixel*Controller` 实现，避免拆出第二套状态语义。
 */
public typealias PageController = PixelPagerController
/** 保留 `PixelControllerAliases` 对 `ScrollController` 的稳定源码别名，避免 artifact 拆分破坏旧导入路径。 */
public typealias ScrollController = PixelListController
/** 保留 `PixelControllerAliases` 对 `TextEditingController` 的稳定源码别名，避免 artifact 拆分破坏旧导入路径。 */
public typealias TextEditingController = PixelTextFieldController
