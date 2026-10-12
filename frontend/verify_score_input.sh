#!/bin/bash

# 成绩录入功能文件验证脚本

echo "=================================="
echo "成绩录入功能文件验证"
echo "=================================="
echo ""

# 检查新建的文件
echo "✅ 检查新建文件..."
files=(
  "src/views/score/input.vue"
  "src/views/score/input-table.vue"
  "src/components/ScoreImport.vue"
  "SCORE_INPUT_README.md"
  "SCORE_INPUT_IMPLEMENTATION.md"
  "SCORE_INPUT_TEST_CHECKLIST.md"
)

for file in "${files[@]}"; do
  if [ -f "$file" ]; then
    echo "  ✓ $file"
  else
    echo "  ✗ $file (缺失)"
  fi
done

echo ""
echo "✅ 检查修改的文件..."
modified_files=(
  "src/api/score.ts"
  "src/views/score/index.vue"
  "src/router/index.ts"
  "src/main.ts"
  "vite.config.ts"
)

for file in "${modified_files[@]}"; do
  if [ -f "$file" ]; then
    echo "  ✓ $file"
  else
    echo "  ✗ $file (缺失)"
  fi
done

echo ""
echo "✅ 检查依赖安装..."
if [ -d "node_modules/xlsx" ]; then
  echo "  ✓ xlsx 已安装"
else
  echo "  ✗ xlsx 未安装"
  echo "    运行: npm install xlsx"
fi

echo ""
echo "✅ 统计代码行数..."
total_lines=0
for file in "src/views/score/input.vue" "src/views/score/input-table.vue" "src/components/ScoreImport.vue"; do
  if [ -f "$file" ]; then
    lines=$(wc -l < "$file")
    echo "  $file: $lines 行"
    total_lines=$((total_lines + lines))
  fi
done
echo "  总计: $total_lines 行"

echo ""
echo "=================================="
echo "验证完成！"
echo "=================================="
echo ""
echo "📋 下一步操作:"
echo "  1. 启动后端服务"
echo "  2. 运行 npm run dev 启动前端"
echo "  3. 访问 http://localhost:3000/score"
echo "  4. 按照 SCORE_INPUT_TEST_CHECKLIST.md 进行测试"
echo ""
