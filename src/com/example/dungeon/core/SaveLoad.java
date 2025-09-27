package com.example.dungeon.core;

import com.example.dungeon.model.*;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

public class SaveLoad {
    private static final Path SAVE = Paths.get("save.txt");
    private static final Path SCORES = Paths.get("scores.csv");

    static {
        WorldInfo.touch("SaveLoad");
    }

    public static void save(GameState s) {
        try (BufferedWriter w = Files.newBufferedWriter(SAVE)) {
            // Сохраняем игрока
            Player p = s.getPlayer();
            w.write("player;" + p.getName() + ";" + p.getHp() + ";" + p.getAttack());
            w.newLine();

            // Сохраняем инвентарь
            String inv = p.getInventory().stream()
                    .map(i -> i.getClass().getSimpleName() + ":" + i.getName())
                    .collect(Collectors.joining(","));
            w.write("inventory;" + (inv.isEmpty() ? "empty" : inv));
            w.newLine();

            // Сохраняем текущую комнату
            w.write("current_room;" + s.getCurrent().getName());
            w.newLine();

            // Сохраняем все комнаты и их состояние
            w.write("==rooms_start==");
            w.newLine();
            saveRooms(w, s.getAllRooms());
            w.write("==rooms_end==");
            w.newLine();

            // Сохраняем связи между комнатами
            w.write("==connections_start==");
            w.newLine();
            saveConnections(w, s.getAllRooms());
            w.write("==connections_end==");
            w.newLine();

            System.out.println("Сохранено в " + SAVE.toAbsolutePath());
            writeScore(p.getName(), s.getScore());

        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось сохранить игру", e);
        }
    }

    private static void saveRooms(BufferedWriter w, Collection<Room> rooms) throws IOException {
        for (Room room : rooms) {
            w.write("room;" + room.getName() + ";" + room.getDescription().replace(";", ","));
            w.newLine();

            // Сохраняем предметы в комнате
            if (!room.getItems().isEmpty()) {
                String roomItems = room.getItems().stream()
                        .map(i -> i.getClass().getSimpleName() + ":" + i.getName().replace(":", "-"))
                        .collect(Collectors.joining(","));
                w.write("room_items;" + room.getName() + ";" + roomItems);
                w.newLine();
            }

            // Сохраняем монстра в комнате
            if (room.getMonster() != null) {
                Monster m = room.getMonster();
                w.write("room_monster;" + room.getName() + ";" + m.getName() + ";" + m.getLevel() + ";" + m.getHp());
                w.newLine();
            }
        }
    }

    private static void saveConnections(BufferedWriter w, Collection<Room> rooms) throws IOException {
        for (Room room : rooms) {
            for (Map.Entry<String, Room> entry : room.getNeighbors().entrySet()) {
                w.write("connection;" + room.getName() + ";" + entry.getKey() + ";" + entry.getValue().getName());
                w.newLine();
            }
        }
    }

    public static void load(GameState s) {
        if (!Files.exists(SAVE)) {
            System.out.println("Сохранение не найдено.");
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(SAVE)) {
            Map<String, Room> roomMap = new HashMap<>();
            String currentRoomName = null;
            boolean inRoomsSection = false;
            boolean inConnectionsSection = false;

            String line;
            while ((line = r.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                if (line.equals("==rooms_start==")) {
                    inRoomsSection = true;
                    continue;
                } else if (line.equals("==rooms_end==")) {
                    inRoomsSection = false;
                    continue;
                } else if (line.equals("==connections_start==")) {
                    inConnectionsSection = true;
                    continue;
                } else if (line.equals("==connections_end==")) {
                    inConnectionsSection = false;
                    continue;
                }

                String[] parts = line.split(";", 2);
                if (parts.length < 2) continue;

                String key = parts[0];
                String data = parts[1];

                if (inRoomsSection) {
                    // Загрузка комнат
                    String[] roomData = data.split(";");
                    switch (key) {
                        case "room":
                            if (roomData.length >= 2) {
                                Room room = new Room(roomData[0], roomData[1]);
                                roomMap.put(roomData[0], room);
                            }
                            break;
                        case "room_items":
                            if (roomData.length >= 2) {
                                Room room = roomMap.get(roomData[0]);
                                if (room != null) {
                                    String[] items = roomData[1].split(",");
                                    for (String itemStr : items) {
                                        String[] itemParts = itemStr.split(":", 2);
                                        if (itemParts.length == 2) {
                                            room.getItems().add(createItem(itemParts[0], itemParts[1]));
                                        }
                                    }
                                }
                            }
                            break;
                        case "room_monster":
                            if (roomData.length >= 4) {
                                Room room = roomMap.get(roomData[0]);
                                if (room != null) {
                                    Monster monster = new Monster(roomData[1],
                                            Integer.parseInt(roomData[2]),
                                            Integer.parseInt(roomData[3]));
                                    room.setMonster(monster);
                                }
                            }
                            break;
                    }
                } else if (inConnectionsSection) {
                    // Загрузка связей между комнатами
                    if (key.equals("connection")) {
                        String[] connData = data.split(";");
                        if (connData.length >= 3) {
                            Room fromRoom = roomMap.get(connData[0]);
                            Room toRoom = roomMap.get(connData[2]);
                            if (fromRoom != null && toRoom != null) {
                                fromRoom.getNeighbors().put(connData[1], toRoom);
                            }
                        }
                    }
                } else {
                    // Основные данные
                    switch (key) {
                        case "player":
                            String[] playerData = data.split(";");
                            if (playerData.length >= 3) {
                                Player p = s.getPlayer();
                                p.setName(playerData[0]);
                                p.setHp(Integer.parseInt(playerData[1]));
                                p.setAttack(playerData.length >= 3 ? Integer.parseInt(playerData[2]) : 5);
                            }
                            break;
                        case "inventory":
                            if (!data.equals("empty")) {
                                Player p = s.getPlayer();
                                p.getInventory().clear();
                                String[] items = data.split(",");
                                for (String itemStr : items) {
                                    String[] itemParts = itemStr.split(":", 2);
                                    if (itemParts.length == 2) {
                                        p.getInventory().add(createItem(itemParts[0], itemParts[1]));
                                    }
                                }
                            }
                            break;
                        case "current_room":
                            currentRoomName = data;
                            break;
                    }
                }
            }

            // Устанавливаем текущую комнату
            if (currentRoomName != null && roomMap.containsKey(currentRoomName)) {
                s.setCurrent(roomMap.get(currentRoomName));
                s.setAllRooms(new ArrayList<>(roomMap.values()));
                System.out.println("Игра загружена! Комнат восстановлено: " + roomMap.size());
            } else {
                System.out.println("Ошибка: не удалось восстановить текущую комнату");
            }

        } catch (IOException e) {
            throw new UncheckedIOException("Не удалось загрузить игру", e);
        } catch (Exception e) {
            System.out.println("Ошибка загрузки: " + e.getMessage());
        }
    }

    private static Item createItem(String type, String name) {
        return switch (type) {
            case "Potion" -> new Potion(name, 5);
            case "Key" -> new Key(name);
            case "Weapon" -> new Weapon(name, 3);
            default -> new Potion(name, 5);
        };
    }

    public static void printScores() {
        if (!Files.exists(SCORES)) {
            System.out.println("Пока нет результатов.");
            return;
        }
        try (BufferedReader r = Files.newBufferedReader(SCORES)) {
            System.out.println("=== ТАБЛИЦА ЛИДЕРОВ ===");
            System.out.println("Место Игрок            Очки");
            System.out.println("---------------------------");

            List<Score> scores = r.lines()
                    .skip(1)
                    .map(l -> l.split(","))
                    .filter(a -> a.length >= 3)
                    .map(a -> new Score(a[1].trim(), Integer.parseInt(a[2].trim())))
                    .sorted(Comparator.comparingInt(Score::score).reversed())
                    .limit(10)
                    .toList();

            if (scores.isEmpty()) {
                System.out.println("   Нет записей");
            } else {
                for (int i = 0; i < scores.size(); i++) {
                    Score score = scores.get(i);
                    System.out.printf("%2d.    %-15s %d%n", i + 1, score.player(), score.score());
                }
            }

        } catch (IOException e) {
            System.err.println("Ошибка чтения результатов: " + e.getMessage());
        }
    }

    public static void writeScore(String player, int score) {
        try {
            boolean header = !Files.exists(SCORES);
            try (BufferedWriter w = Files.newBufferedWriter(SCORES,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
                if (header) {
                    w.write("ts,player,score");
                    w.newLine();
                }
                w.write(LocalDateTime.now() + "," + player + "," + score);
                w.newLine();
            }
        } catch (IOException e) {
            System.err.println("Не удалось записать очки: " + e.getMessage());
        }
    }

    private record Score(String player, int score) {}
}