package com.mengyu.aeron.archive.replication.client;

public enum State
{
    AERON_CREATED,
    POLLING_SUBSCRIPTION,
    SWITCH_TO_BACKUP,
    POLLING_BACKUP_SUBSCRIPTION
}
