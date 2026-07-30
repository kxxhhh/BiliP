#!/bin/bash
set -e
PLUGIN_NAME="emoji-bomb-plugin"
PLUGIN_VERSION="1.0.0"
OUTPUT_DIR="build/distributions"

echo "🎭 开始打包随机表情包炸弹插件..."
mkdir -p "$OUTPUT_DIR"
TEMP_DIR=$(mktemp -d)
echo "📁 创建临时目录: $TEMP_DIR"

cat > "$TEMP_DIR/plugin-manifest.json" << 'EOF'
{
  "pluginId": "com.bilipai.emoji-bomb",
  "displayName": "随机表情包炸弹",
  "version": "1.0.0",
  "apiVersion": 1,
  "entryClassName": "com.android.purebilibili.feature.plugin.EmojiBombPlugin",
  "capabilities": ["PLAYER_STATE", "PLUGIN_STORAGE"],
  "description": "随机在视频播放时弹出搞怪表情包，配以搞笑音效，制造欢乐惊喜",
  "author": "BiliPai",
  "minAppVersion": "9.9.9.1"
}
EOF
echo "📋 创建插件manifest..."

# 复制 class 文件（如果存在）
CLASS_FILE="app/build/intermediates/javac/debug/classes/com/android/purebilibili/feature/plugin/EmojiBombPlugin.class"
if [ -f "$CLASS_FILE" ]; then
    echo "📦 复制编译后的类文件..."
    cp -r "$CLASS_FILE" "$TEMP_DIR/"
else
    echo "⚠️  class 文件不存在，将只包含 manifest"
fi

echo "📦 打包..."
cd "$TEMP_DIR"
zip -r "$OLDPWD/$OUTPUT_DIR/${PLUGIN_NAME}-${PLUGIN_VERSION}.bpplugin" .
cd - > /dev/null
rm -rf "$TEMP_DIR"
echo "✅ 打包完成! 输出: $OUTPUT_DIR/${PLUGIN_NAME}-${PLUGIN_VERSION}.bpplugin"
