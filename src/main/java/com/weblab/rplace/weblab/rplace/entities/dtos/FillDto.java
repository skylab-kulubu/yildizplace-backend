package com.weblab.rplace.weblab.rplace.entities.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class FillDto {

    private int startX;

    private int startY;

    private int endX;

    private int endY;

    private String color;

    private String key;

}
