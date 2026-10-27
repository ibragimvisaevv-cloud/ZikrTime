#!/usr/bin/env bash
# Проверка логики iPhone-версии без Mac: компилируем файлы без SwiftUI и запускаем проверки.
set -euo pipefail
cd "$(dirname "$0")/../.."
mkdir -p build-logic
cp ios/Tests/LogicCheck.swift build-logic/main.swift
swiftc -O -o build-logic/logic-check \
  ios/ZikrTime/Core/PrayerCalculator.swift \
  ios/ZikrTime/Core/Content.swift \
  ios/ZikrTime/Core/Dumrf.swift \
  ios/ZikrTime/Core/ShopCatalog.swift \
  ios/ZikrTime/Core/Ramadan.swift \
  ios/ZikrTime/Core/Zakat.swift \
  ios/ZikrTime/Core/CommunityModels.swift \
  ios/ZikrTime/Core/FajrPlan.swift \
  build-logic/main.swift
./build-logic/logic-check "$(pwd)"
swiftc -O -o build-logic/serial-update-check \
  ios/ZikrTime/Core/SerialUpdate.swift \
  ios/Tests/SerialUpdateCheck.swift
./build-logic/serial-update-check
