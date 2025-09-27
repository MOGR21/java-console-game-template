package com.example.dungeon.model;

import java.util.*;

public class GameState {
    private Player player;
    private Room current;
    private int score;
    private List<Room> allRooms = new ArrayList<>();

    public Player getPlayer() {
        return player;
    }

    public void setPlayer(Player p) {
        this.player = p;
    }

    public Room getCurrent() {
        return current;
    }

    public void setCurrent(Room r) {
        this.current = r;
    }

    public int getScore() {
        return score;
    }

    public void addScore(int d) {
        this.score += d;
    }

    public List<Room> getAllRooms() {
        return allRooms;
    }

    public void setAllRooms(List<Room> rooms) {
        this.allRooms = rooms;
    }
}