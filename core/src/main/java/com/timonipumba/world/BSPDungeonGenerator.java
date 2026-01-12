package com.timonipumba.world;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class BSPDungeonGenerator {
    private static final int MIN_ROOM_SIZE = 6;
    private static final int MAX_ROOM_SIZE = 12;
    
    private int width;
    private int height;
    private int[][] tiles;
    private Random random;
    private List<Room> rooms;

    public BSPDungeonGenerator(int width, int height, long seed) {
        this.width = width;
        this.height = height;
        this.random = new Random(seed);
        this.tiles = new int[width][height];
        this.rooms = new ArrayList<>();
    }

    public int[][] generate() {
        // fill with walls
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                tiles[x][y] = 1; // 1 is wall
            }
        }

        // root node
        BSPNode root = new BSPNode(0, 0, width, height);
        split(root, 0);

        // roomify
        createRooms(root);

        // corridors
        connectRooms(root);

        return tiles;
    }

    private void split(BSPNode node, int depth) {
        if (depth >= 4) {
            return;
        }

        boolean horizontal = random.nextBoolean();
        
        if (horizontal) {
            // too small
            if (node.height <= MIN_ROOM_SIZE * 2) {
                return;
            }
            int splitPos = random.nextInt(node.height - MIN_ROOM_SIZE * 2) + MIN_ROOM_SIZE;
            node.left = new BSPNode(node.x, node.y, node.width, splitPos);
            node.right = new BSPNode(node.x, node.y + splitPos, node.width, node.height - splitPos);
        } else {
            if (node.width <= MIN_ROOM_SIZE * 2) {
                return;
            }
            int splitPos = random.nextInt(node.width - MIN_ROOM_SIZE * 2) + MIN_ROOM_SIZE;
            node.left = new BSPNode(node.x, node.y, splitPos, node.height);
            node.right = new BSPNode(node.x + splitPos, node.y, node.width - splitPos, node.height);
        }

        split(node.left, depth + 1);
        split(node.right, depth + 1);
    }

    private void createRooms(BSPNode node) {
        if (node.left == null && node.right == null) {
            // leaf == room
            int maxRoomWidth = Math.max(1, node.width - 2);
            int maxRoomHeight = Math.max(1, node.height - 2);

            // fixme: handle tiny rooms
            int roomWidth;
            if (maxRoomWidth <= MIN_ROOM_SIZE) {
                roomWidth = maxRoomWidth;
            } else {
                roomWidth = Math.min(random.nextInt(MAX_ROOM_SIZE - MIN_ROOM_SIZE + 1) + MIN_ROOM_SIZE, maxRoomWidth);
            }

            int roomHeight;
            if (maxRoomHeight <= MIN_ROOM_SIZE) {
                roomHeight = maxRoomHeight;
            } else {
                roomHeight = Math.min(random.nextInt(MAX_ROOM_SIZE - MIN_ROOM_SIZE + 1) + MIN_ROOM_SIZE, maxRoomHeight);
            }

            int roomX = node.x + random.nextInt(Math.max(1, node.width - roomWidth - 1)) + 1;
            int roomY = node.y + random.nextInt(Math.max(1, node.height - roomHeight - 1)) + 1;

            Room room = new Room(roomX, roomY, roomWidth, roomHeight);
            rooms.add(room);
            node.room = room;

            // dig it out
            for (int x = roomX; x < roomX + roomWidth; x++) {
                for (int y = roomY; y < roomY + roomHeight; y++) {
                    if (x >= 0 && x < width && y >= 0 && y < height) {
                        tiles[x][y] = 0; // floor
                    }
                }
            }
        } else {
            if (node.left != null) createRooms(node.left);
            if (node.right != null) createRooms(node.right);
        }
    }

    private void connectRooms(BSPNode node) {
        if (node.left == null || node.right == null) {
            return;
        }

        Room leftRoom = getRoom(node.left);
        Room rightRoom = getRoom(node.right);

        if (leftRoom != null && rightRoom != null) {
            int leftCenterX = leftRoom.x + leftRoom.width / 2;
            int leftCenterY = leftRoom.y + leftRoom.height / 2;
            int rightCenterX = rightRoom.x + rightRoom.width / 2;
            int rightCenterY = rightRoom.y + rightRoom.height / 2;

            // Create L-shaped corridor
            if (random.nextBoolean()) {
                createHorizontalCorridor(leftCenterX, rightCenterX, leftCenterY);
                createVerticalCorridor(leftCenterY, rightCenterY, rightCenterX);
            } else {
                createVerticalCorridor(leftCenterY, rightCenterY, leftCenterX);
                createHorizontalCorridor(leftCenterX, rightCenterX, rightCenterY);
            }
        }

        connectRooms(node.left);
        connectRooms(node.right);
    }

    private Room getRoom(BSPNode node) {
        if (node.room != null) {
            return node.room;
        }
        
        Room leftRoom = null;
        Room rightRoom = null;
        
        if (node.left != null) {
            leftRoom = getRoom(node.left);
        }
        if (node.right != null) {
            rightRoom = getRoom(node.right);
        }
        
        return leftRoom != null ? leftRoom : rightRoom;
    }

    private void createHorizontalCorridor(int x1, int x2, int y) {
        int start = Math.min(x1, x2);
        int end = Math.max(x1, x2);
        for (int x = start; x <= end; x++) {
            for (int dy = -4; dy <= 4; dy++) {
                int ny = y + dy;
                if (x >= 0 && x < width && ny >= 0 && ny < height) {
                    tiles[x][ny] = 0;
                }
            }
        }
    }

    private void createVerticalCorridor(int y1, int y2, int x) {
        int start = Math.min(y1, y2);
        int end = Math.max(y1, y2);
        for (int y = start; y <= end; y++) {
            for (int dx = -4; dx <= 4; dx++) {
                int nx = x + dx;
                if (nx >= 0 && nx < width && y >= 0 && y < height) {
                    tiles[nx][y] = 0;
                }
            }
        }
    }

    public List<Room> getRooms() {
        return rooms;
    }

    public static class BSPNode {
        int x, y, width, height;
        BSPNode left, right;
        Room room;

        public BSPNode(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public static class Room {
        public int x, y, width, height;

        public Room(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }

        public int getCenterX() {
            return x + width / 2;
        }

        public int getCenterY() {
            return y + height / 2;
        }
    }
}
