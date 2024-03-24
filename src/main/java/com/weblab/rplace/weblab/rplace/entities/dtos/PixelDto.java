package com.weblab.rplace.weblab.rplace.entities.dtos;

import jakarta.persistence.Entity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PixelDto {

    private int x;

    private int y;

    private String color;

}
