package fr.ujm.turgal.api.picture;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Optional;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import javax.persistence.TypedQuery;
import javax.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import fr.ujm.turgal.model.Picture;
import fr.ujm.turgal.model.PictureRepository;
import fr.ujm.turgal.model.User;
import fr.ujm.turgal.model.UserRepository;

/**
 * PictureRestController
 */
@RestController
@RequestMapping("/api/v1")
@CrossOrigin(origins = "http://localhost:4200")
public class PictureRestController {

    private static final String UPLOAD_PATH = "./src/main/resources/static/images/";
    private static final String IMAGES_PATH = "http://localhost:9090/images/";
    private static final int PAGE_SIZE = 20;

    Logger logger = LoggerFactory.getLogger(this.getClass());

    @Autowired
    PictureRepository picRepo;

    @Autowired
    UserRepository userRepo;

    @PersistenceContext
    EntityManager entityManager;

    @GetMapping(value = "/pictures/{username}/{page}", produces = { "application/json" })
    public ResponseEntity<MultiValueMap<String, Object>> getPictures(@PathVariable String username,
            @PathVariable int page) throws IOException, InterruptedException {
        Long id = userRepo.findByUsername(username).get().getId();
        MultiValueMap<String, Object> pictures = new LinkedMultiValueMap<>();
        File uploadRootDir = new File(UPLOAD_PATH);
        if (!uploadRootDir.exists()) {
            uploadRootDir.mkdirs();
        }
        TypedQuery<Picture> query = entityManager.createQuery("From Picture p Where user_id=" + id, Picture.class);
        query.setFirstResult((page - 1) * PAGE_SIZE);
        query.setMaxResults(PAGE_SIZE);
        for (Picture p : query.getResultList()) {
            pictures.add("id", p.getId());
            pictures.add("title", p.getTitle());
            pictures.add("height", p.getHeight());
            pictures.add("width", p.getWidth());
            pictures.add("size", p.getSize());
            File file = new File(uploadRootDir.getAbsolutePath() + File.separator + id + p.getTitle());
            BufferedOutputStream stream = new BufferedOutputStream(new FileOutputStream(file));
            stream.write(p.getImage());
            stream.close();
            pictures.add("image", IMAGES_PATH + id + p.getTitle());
        }
        TypedQuery<Long> queryTotal = entityManager.createQuery("Select count(p.id) From Picture p Where user_id=" + id,
                Long.class);
        pictures.add("total", (queryTotal.getSingleResult() + PAGE_SIZE - 1) / PAGE_SIZE);
        Thread.sleep(2000);
        return ResponseEntity.status(HttpStatus.OK).body(pictures);
    }

    @PostMapping(value = "/pictures", consumes = { "multipart/form-data" })
    public ResponseEntity<String> importPictures(HttpServletRequest request,
            @RequestParam(name = "pictures") MultipartFile[] pictures) throws IOException {
        String[] widths = request.getParameterValues("widths");
        String[] heights = request.getParameterValues("heights");
        String[] sizes = request.getParameterValues("sizes");
        String[] username = request.getParameterValues("username");
        Optional<User> user = userRepo.findByUsername(username[0]);
        int i = 0;
        while (i < sizes.length) {
            Picture picture = new Picture();
            MultipartFile file = pictures[i];
            picture.setWidth(Integer.valueOf(widths[i]));
            picture.setHeight(Integer.valueOf(heights[i]));
            picture.setSize(Integer.valueOf(sizes[i]));
            picture.setTitle(file.getOriginalFilename());
            picture.setImage(file.getBytes());
            picture.setUser(user.get());
            picRepo.save(picture);
            i++;
        }
        return ResponseEntity.status(HttpStatus.OK).body(null);
    }

    @PutMapping(value = "/pictures", consumes = { "multipart/form-data" })
    public ResponseEntity<String> removePictures(HttpServletRequest request,
            @RequestParam(name = "pictures") Long[] ids) {
        String[] username = request.getParameterValues("username");
        Optional<User> user = userRepo.findByUsername(username[0]);
        for (Long id : ids) {
            Picture p = picRepo.findById(id).get();
            if (p.getUser().getId() == user.get().getId()) {
                picRepo.delete(p);
            }
        }
        return ResponseEntity.status(HttpStatus.OK).body(null);
    }

}