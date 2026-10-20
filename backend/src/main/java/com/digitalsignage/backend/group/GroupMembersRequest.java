package com.digitalsignage.backend.group;

import java.util.List;

public record GroupMembersRequest(List<Long> deviceIds) {
}
