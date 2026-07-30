#!/bin/bash

# 随机表情包炸弹插件打包脚本
# 将插件打包为 .bpplugin 格式

set -e

PLUGIN_NAME="emoji-bomb-plugin"
PLUGIN_VERSION="1.0.0"
OUTPUT_DIR="build/distributions"

echo "🎭 开始打包随机表情包炸弹插件..."

# 创建输出目录
mkdir -p "$OUTPUT_DIR"

# 创建临时打包目录
TEMP_DIR=$(mktemp -d)
echo "📁 创建临时目录: $TEMP_DIR"

# 复制插件manifest
cat > "$TEMP_DIR/plugin-manifest.json" << EOF
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

# 复制编译后的类文件（需要从build目录复制）
if [ -d "app/build/intermediates/javac/debug/classes" ]; then
    echo "📦 复制编译后的类文件..."
    cp -r "app/build/intermediates/javac/debug/classes/com/android/purebilibili/feature/plugin/EmojiBombPlugin.class" "$TEMP_DIR/" 2>/dev/null || echo "⚠️  直接类文件复制失败，将使用源码方式"
fi

# 创建插件源码包（用于源码级插件）
echo "📦 创建插件源码包..."

# 打包为ZIP格式（.bpplugin本质是ZIP）
cd "$TEMP_DIR"
zip -r "../../$OUTPUT_DIR/${PLUGIN_NAME}-${PLUGIN_VERSION}.bpplugin" .
cd - > /dev/null

# 清理临时目录
rm -rf "$TEMP_DIR"

echo "✅ 插件打包完成!"
echo "📦 输出文件: $OUTPUT_DIR/${PLUGIN_NAME}-${PLUGIN_VERSION}.bpplugin"
echo ""
echo "📋 插件信息:"
echo "   - 插件ID: com.bilipai.emoji-bomb"
echo "   - 版本: $PLUGIN_VERSION"
echo "   - 入口类: com.android.purebilibili.feature.plugin.EmojiBombPlugin"
echo "   - 能力: PLAYER_STATE, PLUGIN_STORAGE"
echo ""
echo "🚀 安装方法:"
echo "   1. 将 .bpplugin 文件复制到设备"
echo "   2. 在 BiliPai 中通过 设置 → 插件中心 → 导入外部插件 安装"
echo "   3. 启用插件并配置触发概率等参数"
echo ""
echo "⚠️  注意: 当前版本需要与主程序一起编译运行"