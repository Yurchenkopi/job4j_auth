package ru.job4j.auth.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import ru.job4j.auth.model.Person;
import ru.job4j.auth.service.PersonService;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Pattern;

@RestController
@AllArgsConstructor
@RequestMapping("/person")
public class PersonController {
    private final PersonService simplePersonService;

    private PasswordEncoder encoder;

    private final ObjectMapper objectMapper;

    private static final Logger LOG = LoggerFactory.getLogger(PersonController.class.getName());

    @GetMapping("/")
    public List<Person> findAll() {
        return simplePersonService.findAll();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Person> findById(@PathVariable int id) {
        if (id == 0) {
            throw new IllegalArgumentException("User id must not be equal to zero.");
        }
        return simplePersonService.findById(id)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND, "User is not found. Please, check id."
                ));
    }

    @PostMapping({"/", "/sign-up"})
    public ResponseEntity<Person> create(@RequestBody Person person) {
        if (person.getLogin() == null || person.getPassword() == null) {
            throw new NullPointerException("Username and password mustn't be empty");
        }
        if (!passwordIsValid(person.getPassword())) {
            throw new IllegalArgumentException("Password must contain at least one lowercase and at least one uppercase letter.");
        }
        person.setPassword(encoder.encode(person.getPassword()));
        return simplePersonService.save(person)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.status(409).build());
    }

    @PutMapping("/")
    public ResponseEntity<Void> update(@RequestBody Person person) {
        if (person.getId() == 0) {
            throw new IllegalArgumentException("User id must not be equal to zero.");
        }
        if (person.getLogin() == null || person.getPassword() == null) {
            throw new NullPointerException("Username and password mustn't be empty");
        }
        if (!passwordIsValid(person.getPassword())) {
            throw new IllegalArgumentException("Password must contain at least one lowercase and at least one uppercase letter.");
        }
        ResponseEntity<Void> status = ResponseEntity.notFound().build();
        boolean isUpdated = simplePersonService.update(person);
        if (isUpdated) {
            status = ResponseEntity.ok().build();
        }
        return status;
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable int id) {
        if (id == 0) {
            throw new IllegalArgumentException("User id must not be equal to zero.");
        }
        ResponseEntity<Void> status = ResponseEntity.notFound().build();
        Person person = new Person();
        person.setId(id);
        boolean isDeleted = simplePersonService.delete(person);
        if (isDeleted) {
            status = ResponseEntity.ok().build();
        }
        return status;
    }

    @ExceptionHandler(value = { IllegalArgumentException.class })
    public void exceptionHandler(Exception e, HttpServletRequest request, HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/json");
        response.getWriter().write(objectMapper.writeValueAsString(new HashMap<>() { {
            put("message", e.getMessage());
            put("type", e.getClass());
        }}));
        LOG.error(e.getLocalizedMessage());
    }

    private boolean passwordIsValid(String password) {
        String regex = "^(?=.*[a-z])(?=.*[A-Z]).+$";
        Pattern pattern = Pattern.compile(regex);
        return pattern.matcher(password).find();
    }

    @PatchMapping("/")
    public Person updatePartially(@RequestBody Person person) throws InvocationTargetException, IllegalAccessException {
        var currentOptional = simplePersonService.findById(person.getId());
        if (currentOptional.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }
        var current = currentOptional.get();
        var methods = current.getClass().getDeclaredMethods();
        var namePerMethod = new HashMap<String, Method>();
        for (var method: methods) {
            var name = method.getName();
            if (name.startsWith("get") || name.startsWith("set")) {
                namePerMethod.put(name, method);
            }
        }
        for (var name : namePerMethod.keySet()) {
            if (name.startsWith("get")) {
                var getMethod = namePerMethod.get(name);
                var setMethod = namePerMethod.get(name.replace("get", "set"));
                if (setMethod == null) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                            "Impossible invoke set method from object : " + current + ", Check set and get pairs.");
                }
                var newValue = getMethod.invoke(person);
                if (newValue != null) {
                    setMethod.invoke(current, newValue);
                }
            }
        }
        simplePersonService.save(current);
        return current;
    }


}