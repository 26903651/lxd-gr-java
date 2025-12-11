package com.gdin.inspection.graphrag.pojo;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@NoArgsConstructor
@SuperBuilder
@Data
public class Token {
    private String word;
    private String pos;
}
