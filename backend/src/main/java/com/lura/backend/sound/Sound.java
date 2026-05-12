package com.lura.backend.sound;

import com.lura.backend.category.SoundCategory;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "sounds")
public class Sound {

    @Id
    @Column(length = 80)
    private String id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private SoundCategory category;

    @Column(nullable = false, length = 150)
    private String title;

    @ElementCollection
    @CollectionTable(name = "sound_tags", joinColumns = @JoinColumn(name = "sound_id"))
    @OrderColumn(name = "tag_order")
    @Column(name = "tag", nullable = false, length = 50)
    private List<String> tags = new ArrayList<>();

    @Column(nullable = false)
    private int durationMinutes;

    @Column(nullable = false, length = 1000)
    private String playUrl;

    protected Sound() {
    }

    public Sound(
            String id,
            SoundCategory category,
            String title,
            List<String> tags,
            int durationMinutes,
            String playUrl
    ) {
        this.id = id;
        this.category = category;
        this.title = title;
        this.tags = new ArrayList<>(tags);
        this.durationMinutes = durationMinutes;
        this.playUrl = playUrl;
    }

    public String getId() {
        return id;
    }

    public SoundCategory getCategory() {
        return category;
    }

    public String getTitle() {
        return title;
    }

    public List<String> getTags() {
        return List.copyOf(tags);
    }

    public int getDurationMinutes() {
        return durationMinutes;
    }

    public String getPlayUrl() {
        return playUrl;
    }
}
