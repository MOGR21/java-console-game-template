package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;
import java.util.stream.Collectors;

public class Game {
    private final GameState state = new GameState();
    private final Map<String, Command> commands = new LinkedHashMap<>();

    static {
        WorldInfo.touch("Game");
    }

    public Game() {
        registerCommands();
        bootstrapWorld();
    }

    private void registerCommands() {
        commands.put("about", (ctx, a) -> {
            System.out.println("=== О ИГРЕ ===");
            System.out.println("DungeonMini v2.0 - Консольная RPG игра");
            System.out.println("Разработчик: Могрицкий Сергей Юрьевич");
            System.out.println("Версия: 2.0 (Расширенная)");
            System.out.println("Дата сборки: " + new java.util.Date());
            System.out.println("\n=== ОСОБЕННОСТИ ===");
            System.out.println("• Исследование подземелий с разными комнатами");
            System.out.println("• Система боя с монстрами");
            System.out.println("• Инвентарь и использование предметов");
            System.out.println("• Полная система сохранения/загрузки");
            System.out.println("• Таблица лидеров с рекордами");
            System.out.println("• Работа с памятью и демонстрация GC");
            System.out.println("• Запертые двери и система ключей");
            System.out.println("\n=== УПРАВЛЕНИЕ ===");
            System.out.println("Используйте команды для взаимодействия с миром.");
            System.out.println("Введите 'help' для полного списка команд.");
        });

        commands.put("help", (ctx, a) -> {
            System.out.println("=== КОМАНДЫ ===");
            System.out.println("Основные:");
            System.out.println("  about     - информация об игре");
            System.out.println("  help      - этот список команд");
            System.out.println("  look      - осмотреть текущую комнату");
            System.out.println("  move <dir>- перемещение (north,south,east,west)");
            System.out.println("  take <item>- взять предмет");
            System.out.println("  inventory - показать инвентарь");
            System.out.println("  use <item>- использовать предмет");
            System.out.println("  fight     - сразиться с монстром");
            System.out.println("\nСистемные:");
            System.out.println("  save      - сохранить игру");
            System.out.println("  load      - загрузить игру");
            System.out.println("  scores    - таблица лидеров");
            System.out.println("  exit      - выход из игры");
            System.out.println("\nТехнические:");
            System.out.println("  alloc     - демонстрация работы GC");
            System.out.println("  gc-stats  - статистика памяти");
        });

        commands.put("alloc", (ctx, a) -> {
            // Демонстрация работы с памятью и GC
            System.out.println("=== ДЕМОНСТРАЦИЯ ПАМЯТИ ===");
            Runtime rt = Runtime.getRuntime();

            System.out.println("Создаем 10000 временных объектов...");
            List<Object> tempObjects = new ArrayList<>();
            for (int i = 0; i < 10000; i++) {
                tempObjects.add(new Object());
                tempObjects.add("String-" + i);
                tempObjects.add(new HashMap<String, String>());
            }

            long totalMemory = rt.totalMemory();
            long freeMemoryBefore = rt.freeMemory();
            long usedMemoryBefore = totalMemory - freeMemoryBefore;

            System.out.println("Память до GC:");
            System.out.println("  Использовано: " + (usedMemoryBefore / 1024) + " KB");
            System.out.println("  Свободно: " + (freeMemoryBefore / 1024) + " KB");

            // Освобождаем ссылки и вызываем GC
            tempObjects.clear();
            System.gc();

            try {
                Thread.sleep(100); // Даем время GC поработать
            } catch (InterruptedException e) {
            }

            long freeMemoryAfter = rt.freeMemory();
            long usedMemoryAfter = totalMemory - freeMemoryAfter;

            System.out.println("Память после GC:");
            System.out.println("  Использовано: " + (usedMemoryAfter / 1024) + " KB");
            System.out.println("  Свободно: " + (freeMemoryAfter / 1024) + " KB");
            System.out.println("  Очищено: " + ((freeMemoryAfter - freeMemoryBefore) / 1024) + " KB");
        });

        commands.put("gc-stats", (ctx, a) -> {
            Runtime rt = Runtime.getRuntime();
            long free = rt.freeMemory(), total = rt.totalMemory(), used = total - free;
            long max = rt.maxMemory();
            System.out.println("=== СТАТИСТИКА ПАМЯТИ ===");
            System.out.printf("Использовано:  %,d байт%n", used);
            System.out.printf("Свободно:     %,d байт%n", free);
            System.out.printf("Всего:        %,d байт%n", total);
            System.out.printf("Максимум:     %,d байт%n", max);
            System.out.printf("Загрузка:     %.1f%%%n", (used * 100.0 / total));
        });

        commands.put("look", (ctx, a) -> System.out.println(ctx.getCurrent().describe()));

        commands.put("move", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите направление: north, south, east, west");
            }

            String direction = a.get(0).toLowerCase();
            Room currentRoom = ctx.getCurrent();
            Room neighbor = currentRoom.getNeighbors().get(direction);

            if (neighbor != null) {
                // Проверяем, не заблокирована ли дверь
                if (neighbor.getName().equals("Замок") && direction.equals("east")) {
                    boolean hasKey = ctx.getPlayer().getInventory().stream()
                            .anyMatch(item -> item instanceof Key);
                    if (!hasKey) {
                        throw new InvalidCommandException("Дверь в замок заперта! Нужен ключ.");
                    }
                    System.out.println("Вы использовали ключ чтобы открыть дверь в замок!");
                }

                ctx.setCurrent(neighbor);
                System.out.println("Вы переместились в: " + neighbor.getName());
                System.out.println(neighbor.describe());

                if (neighbor.getMonster() != null) {
                    System.out.println("Осторожно! Здесь находится " + neighbor.getMonster().getName());
                }
            } else {
                throw new InvalidCommandException("Нельзя пойти в направлении: " + direction);
            }
        });

        commands.put("take", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите название предмета");
            }

            String itemName = String.join(" ", a).toLowerCase();
            Room currentRoom = ctx.getCurrent();
            Item foundItem = null;

            Iterator<Item> iterator = currentRoom.getItems().iterator();
            while (iterator.hasNext()) {
                Item item = iterator.next();
                if (item.getName().toLowerCase().contains(itemName)) {
                    foundItem = item;
                    iterator.remove();
                    break;
                }
            }

            if (foundItem != null) {
                ctx.getPlayer().getInventory().add(foundItem);
                System.out.println("Вы взяли: " + foundItem.getName());
                ctx.addScore(2);
            } else {
                throw new InvalidCommandException("Предмет '" + itemName + "' не найден в этой комнате");
            }
        });

        commands.put("inventory", (ctx, a) -> {
            List<Item> inventory = ctx.getPlayer().getInventory();

            if (inventory.isEmpty()) {
                System.out.println("Инвентарь пуст");
            } else {
                String itemsList = inventory.stream()
                        .map(item -> {
                            String type = item instanceof Potion ? "[Зелье]" :
                                    item instanceof Weapon ? "[Оружие]" :
                                            item instanceof Key ? "[Ключ]" : "[Предмет]";
                            return type + " " + item.getName();
                        })
                        .collect(Collectors.joining("\n"));
                System.out.println("=== ИНВЕНТАРЬ ===");
                System.out.println(itemsList);
            }
        });

        commands.put("use", (ctx, a) -> {
            if (a.isEmpty()) {
                throw new InvalidCommandException("Укажите название предмета");
            }

            String itemName = String.join(" ", a).toLowerCase();
            Player player = ctx.getPlayer();
            Item foundItem = null;

            Iterator<Item> iterator = player.getInventory().iterator();
            while (iterator.hasNext()) {
                Item item = iterator.next();
                if (item.getName().toLowerCase().contains(itemName)) {
                    foundItem = item;
                    break;
                }
            }

            if (foundItem == null) {
                throw new InvalidCommandException("Предмет '" + itemName + "' не найден в инвентаре");
            }

            foundItem.apply(ctx);
            ctx.addScore(3);
        });

        commands.put("fight", (ctx, a) -> {
            Room currentRoom = ctx.getCurrent();
            Monster monster = currentRoom.getMonster();
            Player player = ctx.getPlayer();

            if (monster == null) {
                throw new InvalidCommandException("Здесь не с кем сражаться");
            }

            System.out.println("Бой начинается! " + player.getName() + " против " + monster.getName());

            while (player.getHp() > 0 && monster.getHp() > 0) {
                int playerDamage = Math.max(1, player.getAttack() - new Random().nextInt(3));
                monster.setHp(monster.getHp() - playerDamage);
                System.out.println(player.getName() + " атакует и наносит " + playerDamage + " урона. " +
                        monster.getName() + ": " + Math.max(0, monster.getHp()) + " HP");

                if (monster.getHp() <= 0) {
                    System.out.println(monster.getName() + " побежден!");
                    currentRoom.setMonster(null);
                    ctx.addScore(10);
                    break;
                }

                int monsterDamage = Math.max(1, monster.getLevel() - new Random().nextInt(2));
                player.setHp(player.getHp() - monsterDamage);
                System.out.println(monster.getName() + " атакует и наносит " + monsterDamage + " урона. " +
                        player.getName() + ": " + Math.max(0, player.getHp()) + " HP");

                if (player.getHp() <= 0) {
                    System.out.println("Вы погибли в бою...");
                    System.out.println("Игра окончена. Ваш счет: " + ctx.getScore());
                    SaveLoad.writeScore(player.getName(), ctx.getScore());
                    System.exit(0);
                }
            }
        });

        commands.put("save", (ctx, a) -> SaveLoad.save(ctx));
        commands.put("load", (ctx, a) -> SaveLoad.load(ctx));
        commands.put("scores", (ctx, a) -> SaveLoad.printScores());
        commands.put("exit", (ctx, a) -> {
            System.out.println("Сохраняем результат...");
            SaveLoad.writeScore(ctx.getPlayer().getName(), ctx.getScore());
            System.out.println("Пока! Ваш счет: " + ctx.getScore());
            System.exit(0);
        });
    }

    private void bootstrapWorld() {
        Player hero = new Player("Герой", 20, 5);
        state.setPlayer(hero);

        // Создаем комнаты
        Room square = new Room("Площадь", "Каменная площадь с фонтаном в центре.");
        Room forest = new Room("Лес", "Густой лес с высокими деревьями.");
        Room cave = new Room("Пещера", "Темная и сырая пещера.");
        Room mountain = new Room("Горы", "Высокие заснеженные вершины.");
        Room river = new Room("Река", "Быстрая горная река с чистой водой.");
        Room castle = new Room("Замок", "Древний заброшенный замок. Здесь может быть сокровище!");

        // Расставляем предметы
        forest.getItems().add(new Potion("Малое зелье", 5));
        cave.getItems().add(new Potion("Большое зелье", 10));
        square.getItems().add(new Potion("Зелье здоровья", 8));
        mountain.getItems().add(new Weapon("Стальной меч", 3));
        river.getItems().add(new Key("Серебряный ключ"));
        castle.getItems().add(new Potion("Эликсир жизни", 20));

        // Расставляем монстров
        forest.setMonster(new Monster("Волк", 15, 3));
        cave.setMonster(new Monster("Гоблин", 25, 5));
        river.setMonster(new Monster("Тролль", 35, 7));
        castle.setMonster(new Monster("Дракон", 50, 10));

        // Создаем связи между комнатами
        square.getNeighbors().put("north", forest);
        forest.getNeighbors().put("south", square);
        forest.getNeighbors().put("east", cave);
        forest.getNeighbors().put("north", mountain);
        cave.getNeighbors().put("west", forest);
        mountain.getNeighbors().put("south", forest);
        mountain.getNeighbors().put("east", river);
        river.getNeighbors().put("west", mountain);
        river.getNeighbors().put("east", castle); // Запертая дверь!
        castle.getNeighbors().put("west", river);

        // Сохраняем все комнаты в GameState
        List<Room> allRooms = Arrays.asList(square, forest, cave, mountain, river, castle);
        state.setAllRooms(allRooms);
        state.setCurrent(square);

        System.out.println("Мир создан! Доступно комнат: " + allRooms.size());
    }

    public void run() {
        System.out.println("🎮 DungeonMini v2.0 - Расширенная версия");
        System.out.println("📝 Введите 'help' для списка команд или 'about' для информации об игре");
        System.out.println("💡 Совет: найдите ключ чтобы открыть замок!");

        try (BufferedReader in = new BufferedReader(new InputStreamReader(System.in))) {
            while (true) {
                System.out.print("\n> ");
                String line = in.readLine();
                if (line == null) break;
                line = line.trim();
                if (line.isEmpty()) continue;

                List<String> parts = Arrays.asList(line.split("\\s+"));
                String cmd = parts.getFirst().toLowerCase(Locale.ROOT);
                List<String> args = parts.subList(1, parts.size());

                Command command = commands.get(cmd);
                try {
                    if (command == null) throw new InvalidCommandException("Неизвестная команда: " + cmd);
                    command.execute(state, args);
                    state.addScore(1);

                } catch (InvalidCommandException e) {
                    System.out.println("❌ Ошибка: " + e.getMessage());
                } catch (Exception e) {
                    System.out.println("💥 Непредвиденная ошибка: " + e.getMessage());
                }
            }
        } catch (IOException e) {
            System.out.println("Ошибка ввода/вывода: " + e.getMessage());
        }
    }
}