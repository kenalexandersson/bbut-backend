package org.ikuven.bbut.tracking.participant;

import org.ikuven.bbut.tracking.repository.ParticipantRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.env.Environment;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Component
public class ParticipantService {

    private ParticipantRepository repository;

    private Environment environment;

    @Autowired
    public ParticipantService(ParticipantRepository repository, Environment environment) {
        this.repository = repository;
        this.environment = environment;
    }

    public List<Participant> getAllParticipants() {

        return repository.findAll(Sort.by(Sort.Direction.ASC, "firstName")).stream()
                .sorted(onNumberOfLaps()
                        .thenComparing(Participant::getParticipantState)
                        .thenComparing(participant -> participant.getLastLapState().ordinal())
                )
                .collect(Collectors.toList());
    }

    private Comparator<Participant> onNumberOfLaps() {
        return Comparator.comparing(participant -> participant.getLaps().size(), Comparator.reverseOrder());
    }

    public Participant getParticipant(long participantId) {
        return repository.findById(participantId)
                .orElseThrow(() -> new IllegalArgumentException(String.format("No participant found with id %d", participantId)));
    }

    public Participant registerParticipant(String firstName, String lastName, String club, String team, Gender gender, Integer birthYear) {
        return repository.save(Participant.of(0L, firstName, lastName, club, team, gender, birthYear, ParticipantState.REGISTERED));
    }

    public Participant registerParticipant(Participant participant) {
        participant.setId(0L);
        return repository.save(participant);
    }

    public Participant setState(long participantId, ParticipantState participantState) {
        Participant participant = getParticipant(participantId);
        participant.setParticipantState(participantState);

        return repository.save(participant);
    }

    public Participant saveLap(long participantId, LapState lapState, ClientOrigin clientOrigin) {
        return saveLap(participantId, LocalDateTime.now(), lapState, clientOrigin);
    }

    public Participant saveLap(long participantId, LocalDateTime registrationTime, LapState lapState, ClientOrigin clientOrigin) {

        Participant participant = getParticipant(participantId);

        if (ClientOrigin.WEB.equals(clientOrigin) || !lapAlreadyRegistered(participant.getLaps(), registrationTime)) {
            participant.addLap(registrationTime, lapState);

            if (LapState.OVERDUE.equals(lapState)) {
                participant.setParticipantState(ParticipantState.RESIGNED);
            }

            participant = repository.save(participant);
        }

        return participant;
    }

    private boolean lapAlreadyRegistered(List<Lap> laps, LocalDateTime registrationTime) {

        boolean alreadyRegistered = false;

        Optional<Lap> lastLap = laps.stream()
                .reduce((first, second) -> second);

        if (lastLap.isPresent()) {
            long gracePeriod = environment.getProperty("participant.lap.registration-grace-period", Long.class, 15L);
            alreadyRegistered = registrationTime.isBefore(lastLap.get().getRegistrationTime().plusMinutes(gracePeriod));
        }

        return alreadyRegistered;
    }

    public Participant deleteLap(long participantId, Integer lapNumber) {

        Participant participant = getParticipant(participantId);

        if (participant.getLaps().size() > 0) {
            participant.getLaps().remove(lapNumber - 1);
            participant = repository.save(participant);
        }

        return participant;
    }

    public Participant updateLapState(long participantId, Integer lapNumber, LapState lapState) {

        Participant participant = getParticipant(participantId);
        participant.updateLapState(lapState, lapNumber);

        return repository.save(participant);
    }
}
