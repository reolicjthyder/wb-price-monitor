package dev.portfolio.wbmon.domain;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BotConfigService {

    private final BotConfigRepository repository;

    public BotConfigService(BotConfigRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BotConfig getOrCreate(long chatId) {
        return repository.findById(chatId)
                .orElseGet(() -> repository.save(new BotConfig(chatId)));
    }

    @Transactional
    public BotConfig updateInterval(long chatId, int minutes) {
        BotConfig config = getOrCreate(chatId);
        config.setPollIntervalMin(minutes);
        return repository.save(config);
    }
}
