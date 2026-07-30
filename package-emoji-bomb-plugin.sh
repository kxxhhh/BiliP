#!/bin/bash
set -e

PLUGIN_NAME="emoji-bomb-plugin"
PLUGIN_VERSION="1.0.0"
OUTPUT_DIR="build/distributions"
PACK_DIR="build/plugin_pack"   # 永久打包目录

echo "🎭 开始打包随机表情包炸弹插件..."

# 创建输出目录
mkdir -p "$OUTPUT_DIR"

# 清理并重新创建永久打包目录（确保每次都是干净的）
rm -rf "$PACK_DIR"
mkdir -p "$PACK_DIR"

echo "📁 使用永久打包目录: $PACK_DIR"

# 复制插件manifest
cat > "$PACK_DIR/plugin-manifest.json" << EOF
{
  "pluginId": "com.bilipai.emoji-bomb",
  "displayName": "随机表情包炸弹",
  "version": "$PLUGIN_VERSION",
  "apiVersion": 1,
  "entryClassName": "com.android.purebilibili.feature.plugin.EmojiBombPlugin",
  "capabilities": [
    "PLAYER_STATE",
    "PLUGIN_STORAGE"
  ],
  "description": "随机在视频播放时弹出搞怪表情包，配以搞笑音效，制造欢乐惊喜",
  "author": "BiliPai",
  "minAppVersion": "9.9.9.1"
}
EOF

echo "📋 创建插件manifest..."

# 复制编译后的类文件
if [ -d "app/build/intermediates/javac/debug/classes" ]; then
    echo "📦 复制编译后的类文件..."
    mkdir -p "$PACK_DIR/com/android/purebilibili/feature/plugin"
    cp -r "app/build/intermediates/javac/debug/classes/com/android/purebilibili/feature/plugin/"* "$PACK_DIR/com/android/purebilibili/feature/plugin/"
else
    echo "⚠️ 未找到类文件，请先编译项目"
    exit 1
fi

# 打包为ZIP格式（.bpplugin）
cd "$PACK_DIR"
zip -r "../$OUTPUT_DIR/${PLUGIN_NAME}-${PLUGIN_VERSION}.bpplugin" .
cd - > /dev/null

echo "✅ 插件打包完成!"
echo "📦 输出文件: $OUTPUT_DIR/${PLUGIN_NAME}-${PLUGIN_VERSION}.bpplugin"
echo "📁 中间文件保留在: $PACK_DIR （可手动检查）"# 4. 复制编译后的插件类文件（保持完整包路径）
SOURCE_CLASSES_DIR="$PROJECT_ROOT/app/build/intermediates/javac/debug/classes"
if [ ! -d "$SOURCE_CLASSES_DIR" ]; then
    echo "❌ 错误: 未找到编译输出目录，请先编译项目 (./gradlew assembleDebug)"
    exit 1
fi

PACKAGE_SRC="$SOURCE_CLASSES_DIR/$PLUGIN_PACKAGE"
if [ ! -d "$PACKAGE_SRC" ]; then
    echo "❌ 错误: 未找到插件包路径: $PACKAGE_SRC"
    echo "请确认插件类已编译，且包名正确。"
    exit 1
fi

# 在临时目录中创建相同的包路径并复制所有类文件
mkdir -p "$TEMP_DIR/$PLUGIN_PACKAGE"
cp -r "$PACKAGE_SRC/"* "$TEMP_DIR/$PLUGIN_PACKAGE/"
echo "📦 已复制插件类文件（包含所有相关类）"

# 5. （可选）复制资源文件（如果有）
# 若插件有资源文件（如 res/、assets/），取消下面注释并调整路径
# if [ -d "$PROJECT_ROOT/app/src/main/res" ]; then
#     mkdir -p "$TEMP_DIR/res"
#     cp -r "$PROJECT_ROOT/app/src/main/res/"* "$TEMP_DIR/res/"
#     echo "📦 已复制资源文件"
# fi

# 6. 打包为 .bpplugin（ZIP 格式）
cd "$TEMP_DIR"
zip -r "$OUTPUT_DIR/${PLUGIN_NAME}-${PLUGIN_VERSION}.bpplugin" . > /dev/null
cd - > /dev/null

# 7. 清理
rm -rf "$TEMP_DIR"

# 8. 输出结果
echo ""
echo "✅ 打包完成!"
echo "📦 输出文件: $OUTPUT_DIR/${PLUGIN_NAME}-${PLUGIN_VERSION}.bpplugin"
echo ""
echo "📋 插件信息:"
echo "   - 插件ID: com.bilipai.emoji-bomb"
echo "   - 版本: $PLUGIN_VERSION"
echo "   - 入口类: com.android.purebilibili.feature.plugin.EmojiBombPlugin"
echo ""
echo "🚀 安装方法:"
echo "   1. 将 .bpplugin 文件复制到设备"
echo "   2. 在 BiliPai 中通过 设置 → 插件中心 → 导入外部插件 安装"
echo "   3. 启用插件并配置参数"cd "$TEMP_DIR"
zip -r "$OLDPWD/$OUTPUT_DIR/${PLUGIN_NAME}-${PLUGIN_VERSION}.bpplugin" .
cd - > /dev/null
rm -rf "$TEMP_DIR"
echo "✅ 打包完成! 输出: $OUTPUT_DIR/${PLUGIN_NAME}-${PLUGIN_VERSION}.bpplugin"
