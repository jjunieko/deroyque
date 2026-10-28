package com.deroyque.activation;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RequestRepository extends JpaRepository<ActivationRequest, UUID> {}
