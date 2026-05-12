package com.lura.backend.sound;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sounds")
public class SoundController {

    private final SoundService soundService;

    public SoundController(SoundService soundService) {
        this.soundService = soundService;
    }

    @GetMapping
    public List<SoundResponse> getSounds() {
        return soundService.getSounds();
    }

    @GetMapping("/category/{categoryId}")
    public List<SoundResponse> getSoundsByCategory(@PathVariable String categoryId) {
        return soundService.getSoundsByCategory(categoryId);
    }

    @GetMapping("/{soundId}/play")
    public SoundPlayResponse getPlayUrl(@PathVariable String soundId) {
        return soundService.getPlayUrl(soundId);
    }
}
