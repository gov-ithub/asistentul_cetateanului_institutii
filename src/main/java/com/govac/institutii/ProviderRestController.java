package com.govac.institutii;

import com.govac.institutii.db.Provider;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.govac.institutii.db.ProviderRepository;
import com.govac.institutii.db.User;
import com.govac.institutii.db.UserRepository;
import com.govac.institutii.security.JwtTokenUtil;
import com.govac.institutii.security.JwtUser;
import com.govac.institutii.security.JwtUserDetailsService;
import com.govac.institutii.validation.MessageDTO;
import com.govac.institutii.validation.MessageType;
import com.govac.institutii.validation.ProviderAdminDTO;
import java.util.Locale;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestBody;

@RestController
@RequestMapping("/api/providers")
public class ProviderRestController {
    @Autowired
    private MessageSource msgSource;

    @Autowired
    private ProviderRepository providerRepo;
    
    @Autowired
    private UserRepository userRepo;

    @Value("${jwt.header}")
    private String tokenHeader;

    @Autowired
    private JwtTokenUtil jwtTokenUtil;

    @Autowired
    private JwtUserDetailsService userDetailsService;

    @RequestMapping(value = "", method = RequestMethod.GET)
    @PreAuthorize("hasRole('ADMIN') || hasRole('PROVIDER')")
    public ResponseEntity<?> getProviders(
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", defaultValue = "20") int size,
            HttpServletRequest request) {
        String token = request.getHeader(tokenHeader);
        Optional<String> email = jwtTokenUtil.getSubjectFromToken(token);
        return email
                .map((e) -> {
                    JwtUser usr = (JwtUser) userDetailsService
                            .loadUserByUsername(e);
                    if (null == usr) {
                        return ResponseEntity.status(403).body(null);
                    }

                    if (usr.getUser().getRole().equals("ROLE_ADMIN")) {
                        return ResponseEntity.ok(
                                providerRepo.findAll(
                                        new PageRequest(page, size)
                                )
                        );
                    }
                    return ResponseEntity.ok(
                            providerRepo.findByAdminEmail(
                                    e, new PageRequest(page, size)
                            )
                    );
                })
                .orElse(ResponseEntity.status(403).body(null));
    }
    
    @RequestMapping(value = "", method = RequestMethod.POST)
    @PreAuthorize("hasRole('ADMIN') || hasRole('PROVIDER')")
    public ResponseEntity<?> createProvider(
            @RequestBody @Validated ProviderAdminDTO provider,
            HttpServletRequest request){
        String token = request.getHeader(tokenHeader);
        Optional<String> email = jwtTokenUtil.getSubjectFromToken(token);
        if (!email.isPresent()) {
            return ResponseEntity.badRequest().body(
                    new MessageDTO(
                            MessageType.ERROR, 
                            translate("error.provider.admin.nojwt")
                    )
            );
        }
        JwtUser usr = (JwtUser) userDetailsService
                .loadUserByUsername(email.get());
        if (null == usr) {
            return ResponseEntity.badRequest().body(
                    new MessageDTO(
                            MessageType.ERROR,
                            translate("error.provider.admin.nojwt")
                    )
            );
        }
        Boolean isAdmin = usr.getUser().getRole().equals("ROLE_ADMIN");
        
        if (null == provider.admin && isAdmin) {
            return ResponseEntity.badRequest().body(
                    new MessageDTO(
                            MessageType.ERROR,
                            translate("error.provider.admin.notnull")
                    )
            );
        }
        if (null != provider.admin && isAdmin) {
            Optional<User> loadedAdmin = userRepo.findByIdAndRole(
                    provider.admin, 
                    "ROLE_PROVIDER"
            );
            if (!loadedAdmin.isPresent())
                return ResponseEntity.badRequest().body(
                        new MessageDTO(
                                MessageType.ERROR, 
                                translate("error.provider.admin.noentity")
                        )
                );
            Provider toSaveProvider = new Provider(
                    loadedAdmin.get(), provider.name, provider.url
            );
            Provider savedProvider = providerRepo.save(toSaveProvider);
            return ResponseEntity.ok(savedProvider);
        }
        
        Provider toSaveProvider = new Provider(
                usr.getUser(), provider.name, provider.url
        );
        Provider savedProvider = providerRepo.save(toSaveProvider);
        return ResponseEntity.ok(savedProvider);
    }
    
    private String translate(String m) {
        Locale currentLocale = LocaleContextHolder.getLocale();
        return msgSource.getMessage(m, null, currentLocale);
    } 
}
