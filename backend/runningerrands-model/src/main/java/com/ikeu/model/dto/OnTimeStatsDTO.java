package com.ikeu.model.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OnTimeStatsDTO implements Serializable {

    private Long total;

    private Long onTime;
}
