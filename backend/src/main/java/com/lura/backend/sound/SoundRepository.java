package com.lura.backend.sound;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SoundRepository extends JpaRepository<Sound, String> {

    List<Sound> findByCategoryId(String categoryId);
}
