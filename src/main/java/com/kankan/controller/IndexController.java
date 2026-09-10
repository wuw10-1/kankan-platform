package com.kankan.controller;

import com.kankan.model.Video;
import com.kankan.service.RoomService;
import com.kankan.service.VideoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.List;

@Controller
@RequiredArgsConstructor
public class IndexController {

    private final VideoService videoService;
    private final RoomService roomService;

    @GetMapping("/")
    public String index(Model model) {
        List<Video> featuredVideos = videoService.getFeaturedVideos();
        model.addAttribute("featuredVideos", featuredVideos);
        model.addAttribute("publicRooms", roomService.findPublicRooms());
        return "index";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String register() {
        return "register";
    }

    @GetMapping("/room/{roomCode}")
    public String room(@PathVariable String roomCode, Model model) {
        model.addAttribute("roomCode", roomCode);
        return "room";
    }
}