package com.hmdp.dto;

import lombok.Data;

/**
 * @author dingchuan
 */
@Data
public class LoginFormDTO {

  private String phone;
  private String code;
  private String password;
}
