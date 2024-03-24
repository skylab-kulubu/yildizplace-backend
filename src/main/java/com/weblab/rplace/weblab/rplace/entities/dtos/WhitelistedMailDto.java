package com.weblab.rplace.weblab.rplace.entities.dtos;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class WhitelistedMailDto {

    private String mail;

    private String key;
}
