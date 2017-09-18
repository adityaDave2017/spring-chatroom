package com.spring.chatroom.topic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.spring.chatroom.model.Message;
import com.spring.chatroom.model.Request;
import com.spring.chatroom.model.Response;
import com.spring.chatroom.model.ResponseCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;


// Represents a single topic
@SuppressWarnings({"WeakerAccess", "unused", "JavaDoc", "Convert2Lambda"})
public class Room {

    private static final Logger LOGGER = LoggerFactory.getLogger(Room.class);
    private final Gson GSON = new GsonBuilder().create();
    private final ConcurrentMap<String, Viewer> peopleInRoom = new ConcurrentHashMap<>();
    private static final SimpleDateFormat DATE_FORMATTER = new SimpleDateFormat("HH:mm:ss");

    private final String roomName;

    public Room(final String roomName) {
        this.roomName = roomName;
    }

    public String getRoomName() {
        return roomName;
    }

    public Viewer getViewerBySId(String sessionId) {
        return peopleInRoom.get(sessionId);
    }

    public Collection<Viewer> getViewers() {
        return peopleInRoom.values();
    }


    /**
     * Returns whether the room is empty or not
     *
     * @return
     */
    private boolean isRoomEmpty(String sessionId) {
        return peopleInRoom.get(sessionId) != null && peopleInRoom.size() == 1;
    }


    /**
     * Returns the number of members present in room
     *
     * @return
     */
    private int getRoomSize() {
        return peopleInRoom.size();
    }


    /**
     * Returns a list containing the name of members in the room
     *
     * @return
     */
    private List<String> getMembersListExcept(Viewer exception) {
        List<String> members = new ArrayList<>();
        for (Viewer v : getViewers()) {
            if (!exception.getSessionId().equalsIgnoreCase(v.getSessionId())) {
                members.add(v.getViewerName());
            }
        }
        return members;
    }


    /**
     * Add viewer to current room
     *
     * @param viewer
     */
    public void addViewer(Viewer viewer) {
        LOGGER.info("{} added to {} ... {}", viewer.getViewerName(), roomName, viewer.getSessionId());
        peopleInRoom.put(viewer.getSessionId(), viewer);

        // notify viewer of successful join
        Response response = new Response();
        response.setResponseCode(ResponseCode.JOIN_SUCCESSFUL.getCode());
        response.setResponseDesc(ResponseCode.JOIN_SUCCESSFUL.getDescription());
        response.setSessionId(viewer.getSessionId());
        sendTo(viewer, response);

        // notify others of viewer join
        response.setResponseCode(ResponseCode.SOMEONE_JOINED.getCode());
        response.setResponseDesc(ResponseCode.SOMEONE_JOINED.getDescription());
        response.setMessage(new Message(
                        this.roomName,
                        viewer.getViewerName() + " has joined the room",
                DATE_FORMATTER.format(new Date())
                )
        );
        sendExcept(viewer, response);
    }


    /**
     * Remove viewer from current room
     *
     * @param sessionId
     */
    public void removeViewer(String sessionId) {
        Viewer toRemove = peopleInRoom.remove(sessionId);
        Response response = new Response();

        if (toRemove != null) {
            // notify viewer of left successful
            response.setResponseCode(ResponseCode.LEFT_SUCCESSFULLY.getCode());
            response.setResponseDesc(ResponseCode.LEFT_SUCCESSFULLY.getDescription());
            response.setSessionId(sessionId);
            sendTo(toRemove, response);
            toRemove.close();

            // Notify others of viewer removal
            response.setResponseCode(ResponseCode.SOMEONE_LEFT.getCode());
            response.setResponseDesc(ResponseCode.SOMEONE_LEFT.getDescription());
            response.setMessage(
                    new Message(
                            roomName,
                            toRemove.getViewerName() + " left the room.",
                            DATE_FORMATTER.format(new Date())
                    )
            );
            sendToAll(response);
        } else {

            // notify viewer of left failed
            response.setResponseCode(ResponseCode.LEFT_UNSUCCESSFUL.getCode());
            response.setResponseDesc(ResponseCode.LEFT_UNSUCCESSFUL.getDescription());
            sendTo(getViewerBySId(sessionId), response);
        }
    }


    /**
     * Acknoledges the received message. And forwards the message to
     * others in the same room
     *
     * @param request
     * @param viewer
     */
    public void sendMessage(Request request, Viewer viewer) {
        // Acknowledge message received
        Response response = new Response();
        response.setResponseCode(ResponseCode.MESSAGE_RECEIVED.getCode());
        response.setSessionId(viewer.getSessionId());
        response.setResponseDesc(ResponseCode.MESSAGE_RECEIVED.getDescription());
        response.setMessage(request.getMessage());
        sendTo(viewer, response);

        // Forward message to others
        response.setResponseCode(ResponseCode.FORWARD_MESSAGE.getCode());
        response.setResponseDesc(ResponseCode.FORWARD_MESSAGE.getDescription());
        sendExcept(viewer, response);
    }


    /**
     * Sends a list of current room members in the room
     *
     * @param viewer who requested for member list
     */
    public void viewMembers(Viewer viewer) {
        Response response = new Response();
        if (isRoomEmpty(viewer.getSessionId())) {
            response.setResponseCode(ResponseCode.EMPTY_ROOM.getCode());
            response.setResponseDesc(ResponseCode.EMPTY_ROOM.getDescription());
            response.setSessionId(viewer.getSessionId());
        } else {
            response.setResponseCode(ResponseCode.MEMBER_LIST.getCode());
            response.setResponseDesc(ResponseCode.MEMBER_LIST.getDescription());
            response.setSessionId(viewer.getSessionId());
            response.setMessage(
                    new Message(
                            roomName,
                            getMembersListExcept(viewer),
                            DATE_FORMATTER.format(new Date())
                    )
            );
        }
        sendTo(viewer, response);
    }


    /**
     * Sends the provided response to everyone in room except the given one
     *
     * @param exception
     */
    private void sendExcept(Viewer exception, Response response) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (Viewer viewer : peopleInRoom.values()) {
                        if (!viewer.getSessionId().equalsIgnoreCase(exception.getSessionId())) {
                            viewer.sendMessage(GSON.toJson(response));
                        }
                    }
                } catch (IOException exc) {
                    exc.printStackTrace();
                }
            }
        });
        thread.start();
    }


    /**
     * Send message to a particular viewer
     *
     * @param viewer
     */
    public void sendTo(Viewer viewer, Response response) {
        try {
            viewer.sendMessage(GSON.toJson(response));
        } catch (IOException exc) {
            exc.printStackTrace();
        }
    }


    /**
     * Sends message to all the members in the room
     *
     * @param response
     */
    private void sendToAll(Response response) {
        Thread thread = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    for (Viewer viewer : getViewers()) {
                        viewer.sendMessage(GSON.toJson(response));
                    }
                } catch (IOException exc) {
                    exc.printStackTrace();
                }
            }
        });
        thread.start();
    }

}