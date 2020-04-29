/*
 * Copyright (c) 2016 Farooq Khan
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to
 * deal in the Software without restriction, including without limitation the
 * rights to use, copy, modify, merge, publish, distribute, sublicense, and/or
 * sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL
 * THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.jsondb;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import io.jsondb.crypto.ICipher;
import io.jsondb.events.CollectionFileChangeListener;
import io.jsondb.query.Update;
import io.jsondb.query.ddl.CollectionSchemaUpdate;

/**
 * Interface that defines the methods available in JsonDBTemplate
 *
 * @author Farooq Khan
 * @version 1.0 21 Aug 2016
 */
public interface JsonDBOperations {

  /**
   * Re-load the collections from dblocation folder.
   * This functionality is useful if you some other process is going to directly update
   * the collection files in dblocation
   */
  void reLoadDB();

  /**
   * Reloads a particular collection from dblocation directory
   * @param collectionName name of the collection to reload
   */
  void reloadCollection(String collectionName);

  /**
   * adds a CollectionFileChangeListener to db.
   *
   * NOTE: This method uses FileWatchers and on MAC we get now events for file changes so this does not work on Mac
   * @param listener actual listener to add
   */
  void addCollectionFileChangeListener(CollectionFileChangeListener listener);

  /**
   * removes a previously added CollectionFileChangeListener
   * @param listener actual listener to remove
   */
  void removeCollectionFileChangeListener(CollectionFileChangeListener listener);

  /**
   * a method to check if there are any registered CollectionFileChangeListener
   * @return true of there are any registered CollectionFileChangeListeners
   */
  boolean hasCollectionFileChangeListener();

  /**
   * Create an uncapped collection with a name based on the provided entity class.
   *
   * @param entityClass class that determines the collection to create
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void createCollection(Class<T> entityClass);

  /**
   * Create an uncapped collection with the provided name.
   *
   * @param collectionName name of the collection
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void createCollection(String collectionName);

  /**
   * Drop the collection with the name indicated by the entity class.
   *
   * @param entityClass class that determines the collection to drop/delete.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void dropCollection(Class<T> entityClass);

  /**
   * Drop the collection with the given name.
   *
   * @param collectionName name of the collection to drop/delete.
   */
  void dropCollection(String collectionName);

  <T> void updateCollectionSchema(CollectionSchemaUpdate update, Class<T> entityClass);

  /**
   * Update a collection as per the specified CollectionUpdate param.
   * This method is only available by collectionName and not by Class.
   *
   * @param update how to update the Collection
   * @param collectionName  name of the collection to update schema for
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void updateCollectionSchema(CollectionSchemaUpdate update, String collectionName);

  /**
   * A set of collection names.
   *
   * @return list of collection names
   */
  Set<String> getCollectionNames();

  /**
   * The collection name used for the specified class by this template.
   *
   * @param entityClass must not be {@literal null}.
   * @return name of the collection
   */
  String getCollectionName(Class<?> entityClass);

  /**
   * Get a collection by name, creating it if it doesn't exist.
   * The returned collection will be a new copy of the existing collection
   * Modifying its contents will not modify the contents of collection in JsonDB memory.
   *
   * @param entityClass class that determines the name of the collection
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return A copy of the existing collection or a newly created one.
   */
  <T> List<T> getCollection(Class<T> entityClass);

  /**
   * Check to see if a collection with a name indicated by the entity class exists.
   *
   * @param entityClass class that determines the name of the collection
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return true if a collection with the given name is found, false otherwise.
   */
  <T> boolean collectionExists(Class<T> entityClass);

  /**
   * Check to see if a collection with a given name exists.
   *
   * @param collectionName name of the collection
   * @return true if a collection with the given name is found, false otherwise.
   */
  boolean collectionExists(String collectionName);

  /**
   * is a collection readonly,
   * A collection can be readonly if its schema version does not match the actualSchema version
   *
   * @param entityClass class that determines the collection
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return true if collection is readonly
   */
  <T> boolean isCollectionReadonly(Class<T> entityClass);

  <T> boolean isCollectionReadonly(String collectionName);

  /**
   * Map the results of an ad-hoc query on the collection for the entity class to a List of the specified type.
   *
   * @param jxQuery a XPATH query expression
   * @param entityClass the parameterized type of the returned list.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the list of found objects
   */
  <T> List<T> find(String jxQuery, Class<T> entityClass);

  /**
   * Map the results of an ad-hoc query on the specified collection to a List of the specified type.
   *
   * @param jxQuery a XPATH query expression
   * @param collectionName name of the collection to retrieve the objects from
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the list of found objects
   */
  <T> List<T> find(String jxQuery, String collectionName);

  /**
   * Map the results of an ad-hoc query on the collection for the entity class to a List of the specified type.
   *
   * The objects in result are sorted according to the order induced by the specified comparator.
   * All elements in the array must be mutually comparable by the specified comparator (that is, c.compare(e1, e2)
   * must not throw a ClassCastException for any elements e1 and e2 in the array).
   *
   * @param jxQuery a XPATH query expression
   * @param entityClass the parameterized type of the returned list.
   * @param comparator Comparator to use for sorting the objects
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the list of found objects
   */
  <T> List<T> find(String jxQuery, Class<T> entityClass, Comparator<? super T> comparator);

  /**
   * Map the results of an ad-hoc query on the specified collection to a List of the specified type.
   *
   * The objects in result are sorted according to the order induced by the specified comparator.
   * All elements in the array must be mutually comparable by the specified comparator (that is, c.compare(e1, e2)
   * must not throw a ClassCastException for any elements e1 and e2 in the array).
   *
   * @param jxQuery a XPATH query expression
   * @param collectionName name of the collection to retrieve the objects from
   * @param comparator Comparator to use for sorting the objects
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the list of found objects
   */
  <T> List<T> find(String jxQuery, String collectionName, Comparator<? super T> comparator);

  /**
   * Map the results of an ad-hoc query on the specified collection to a List of the specified type.
   *
   * The objects in result are sorted according to the order induced by the specified comparator.
   * All elements in the array must be mutually comparable by the specified comparator (that is, c.compare(e1, e2)
   * must not throw a ClassCastException for any elements e1 and e2 in the array).
   *
   * @param jxQuery a XPATH query expression
   * @param entityClass the parameterized type of the returned list.
   * @param comparator Comparator to use for sorting the objects
   *                   Note: If sorting along with slicing is used then sorting over fields with the 'secret' anotation
   *                   will actually sort the raw encrypted field value, which will be undesirable and probably useless,
   *                   this limitation is for efficiency reasons, when slicing is enabled we defer the deep copy and
   *                   decryption of secret fields until a later stage (which happens after sorting), assuming quite
   *                   some objects may not be selected due to slicing
   *
   *                   However if slicing is not used then the sorting will sort over unencrypted field value.
   *
   * @param slice select the indices to return from the find_result. The behaviour of this slicing feature is similar to
   *              the slicing feature in python or numpy, as much as possible
   *              https://docs.scipy.org/doc/numpy-1.13.0/reference/arrays.indexing.html
   *
   *              A slice is a string notation and the basic slice syntax is i:j:k, where i is the starting index,
   *              j is the stopping index, and k is the step (k != 0). In other words it is start:stop:step
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "1:7:2" returns [T1, T3, T5]
   *
   *              i is inclusive, j is exclusive
   *
   *              Negative i and j are interpreted as n + i and n + j where n is the number of elements found. Negative
   *              k makes stepping go towards smaller indices.
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "-2:10" returns [T8, T9]
   *                      slice = "-3:3:-1" returns [T7, T6, T5, T4]
   *
   *              Assume n is the number of elements in find_result. Then, if i is not given it defaults to 0
   *              for k&gt;0 and n - 1 for k&lt;0 . If j is not given it defaults to n for k&gt;0 and -n-1 for k&lt;0
   *              If k is not given it defaults to 1. Note that :: is the same as : and means select all indices
   *              from find_result.
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "5:" returns [T5, T6, T7, T8, T9]
   *
   *              Assume n is the number of elements in find_result. Then, if j&gt;n then it is considered as n, in case
   *              of negative j it is considered -n.
   *
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the list of found objects
   */
  <T> List<T> find(String jxQuery, Class<T> entityClass, Comparator<? super T> comparator, String slice);

  /**
   * Map the results of an ad-hoc query on the specified collection to a List of the specified type.
   *
   * The objects in result are sorted according to the order induced by the specified comparator.
   * All elements in the array must be mutually comparable by the specified comparator (that is, c.compare(e1, e2)
   * must not throw a ClassCastException for any elements e1 and e2 in the array).
   *
   * @param jxQuery a XPATH query expression
   * @param collectionName name of the collection to retrieve the objects from
   * @param comparator Comparator to use for sorting the objects
   *                   Note: If sorting along with slicing is used then sorting over fields with the 'secret' anotation
   *                   will actually sort the raw encrypted field value, which will be undesirable and probably useless,
   *                   this limitation is for efficiency reasons, when slicing is enabled we defer the deep copy and
   *                   decryption of secret fields until a later stage (which happens after sorting), assuming quite
   *                   some objects may not be selected due to slicing
   *
   *                   However if slicing is not used then the sorting will sort over unencrypted field value.
   *
   * @param slice select the indices to return from the find_result. The behaviour of this slicing feature is similar to
   *              the slicing feature in python or numpy, as much as possible
   *              https://docs.scipy.org/doc/numpy-1.13.0/reference/arrays.indexing.html
   *
   *              A slice is a string notation and the basic slice syntax is i:j:k, where i is the starting index,
   *              j is the stopping index, and k is the step (k != 0). In other words it is start:stop:step
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "1:7:2" returns [T1, T3, T5]
   *
   *              i is inclusive, j is exclusive
   *
   *              Negative i and j are interpreted as n + i and n + j where n is the number of elements found. Negative
   *              k makes stepping go towards smaller indices.
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "-2:10" returns [T8, T9]
   *                      slice = "-3:3:-1" returns [T7, T6, T5, T4]
   *
   *              Assume n is the number of elements in find_result. Then, if i is not given it defaults to 0
   *              for k&gt;0 and n - 1 for k&lt;0 . If j is not given it defaults to n for k&gt;0 and -n-1 for k&lt;0
   *              If k is not given it defaults to 1. Note that :: is the same as : and means select all indices
   *              from find_result.
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "5:" returns [T5, T6, T7, T8, T9]
   *
   *              Assume n is the number of elements in find_result. Then, if j&gt;n then it is considered as n, in case
   *              of negative j it is considered -n.
   *
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the list of found objects
   */
  <T> List<T> find(String jxQuery, String collectionName, Comparator<? super T> comparator, String slice);

  /**
   * Query for a list of objects of type T from the specified collection.
   *
   * @param entityClass the parameterized type of the returned list.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the found collection
   */
  <T> List<T> findAll(Class<T> entityClass);

  /**
   * Query for a list of objects of type T from the specified collection.
   *
   * @param collectionName name of the collection to retrieve the objects from
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the found collection
   */
  <T> List<T> findAll(String collectionName);

  /**
   * Query for a list of objects of type T from the specified collection.
   *
   * @param entityClass the parameterized type of the returned list.
   * @param comparator Comparator to use for sorting the objects
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the found collection
   */
  <T> List<T> findAll(Class<T> entityClass, Comparator<? super T> comparator);

  /**
   * Query for a list of objects of type T from the specified collection.
   *
   * @param collectionName name of the collection to retrieve the objects from
   * @param comparator Comparator to use for sorting the objects
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the found collection
   */
  <T> List<T> findAll(String collectionName, Comparator<? super T> comparator);

  /**
   * Query for a list of objects of type T from the specified collection.
   *
   * @param entityClass the parameterized type of the returned list.
   * @param comparator Comparator to use for sorting the objects
   *                   Note: If sorting along with slicing is used then sorting over fields with the 'secret' anotation
   *                   will actually sort the raw encrypted field value, which will be undesirable and probably useless,
   *                   this limitation is for efficiency reasons, when slicing is enabled we defer the deep copy and
   *                   decryption of secret fields until a later stage (which happens after sorting), assuming quite
   *                   some objects may not be selected due to slicing
   *
   *                   However if slicing is not used then the sorting will sort over unencrypted field value.
   *
   * @param slice select the indices to return from the find_result. The behaviour of this slicing feature is similar to
   *              the slicing feature in python or numpy, as much as possible
   *              https://docs.scipy.org/doc/numpy-1.13.0/reference/arrays.indexing.html
   *
   *              A slice is a string notation and the basic slice syntax is i:j:k, where i is the starting index,
   *              j is the stopping index, and k is the step (k != 0). In other words it is start:stop:step
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "1:7:2" returns [T1, T3, T5]
   *
   *              i is inclusive, j is exclusive
   *
   *              Negative i and j are interpreted as n + i and n + j where n is the number of elements found. Negative
   *              k makes stepping go towards smaller indices.
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "-2:10" returns [T8, T9]
   *                      slice = "-3:3:-1" returns [T7, T6, T5, T4]
   *
   *              Assume n is the number of elements in find_result. Then, if i is not given it defaults to 0
   *              for k&gt;0 and n - 1 for k&lt;0 . If j is not given it defaults to n for k&gt;0 and -n-1 for k&lt;0
   *              If k is not given it defaults to 1. Note that :: is the same as : and means select all indices
   *              from find_result.
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "5:" returns [T5, T6, T7, T8, T9]
   *
   *              Assume n is the number of elements in find_result. Then, if j&gt;n then it is considered as n, in case
   *              of negative j it is considered -n.
   *
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation and member of the baseScanPackage
   * @return the found collection
   */
  <T> List<T> findAll(Class<T> entityClass, Comparator<? super T> comparator, String slice);

  /**
   * Query for a list of objects of type T from the specified collection.
   *
   * @param collectionName name of the collection to retrieve the objects from
   * @param comparator Comparator to use for sorting the objects
   *                   Note: If sorting along with slicing is used then sorting over fields with the 'secret' anotation
   *                   will actually sort the raw encrypted field value, which will be undesirable and probably useless,
   *                   this limitation is for efficiency reasons, when slicing is enabled we defer the deep copy and
   *                   decryption of secret fields until a later stage (which happens after sorting), assuming quite
   *                   some objects may not be selected due to slicing
   *
   *                   However if slicing is not used then the sorting will sort over unencrypted field value.
   *
   * @param slice select the indices to return from the find_result. The behaviour of this slicing feature is similar to
   *              the slicing feature in python or numpy, as much as possible
   *              https://docs.scipy.org/doc/numpy-1.13.0/reference/arrays.indexing.html
   *
   *              A slice is a string notation and the basic slice syntax is i:j:k, where i is the starting index,
   *              j is the stopping index, and k is the step (k != 0). In other words it is start:stop:step
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "1:7:2" returns [T1, T3, T5]
   *
   *              i is inclusive, j is exclusive
   *
   *              Negative i and j are interpreted as n + i and n + j where n is the number of elements found. Negative
   *              k makes stepping go towards smaller indices.
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "-2:10" returns [T8, T9]
   *                      slice = "-3:3:-1" returns [T7, T6, T5, T4]
   *
   *              Assume n is the number of elements in find_result. Then, if i is not given it defaults to 0
   *              for k&gt;0 and n - 1 for k&lt;0 . If j is not given it defaults to n for k&gt;0 and -n-1 for k&lt;0
   *              If k is not given it defaults to 1. Note that :: is the same as : and means select all indices
   *              from find_result.
   *              Example find_result = [T0, T1, T2, T3, T4, T5, T6, T7, T8, T9]
   *                      slice = "5:" returns [T5, T6, T7, T8, T9]
   *
   *              Assume n is the number of elements in find_result. Then, if j&gt;n then it is considered as n, in case
   *              of negative j it is considered -n.
   *
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation and member of the baseScanPackage
   * @return the found collection
   */
  <T> List<T> findAll(String collectionName, Comparator<? super T> comparator, String slice);

  /**
   * Returns a document with the given id mapped onto the given class. The collection the query is ran against will be
   * derived from the given target class as well.
   *
   * @param id the id of the document to return.
   * @param entityClass the type the document shall be converted into.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation and member of the baseScanPackage
   * @return the document with the given id mapped onto the given target class.
   */
  <T> T findById(Object id, Class<T> entityClass);

  /**
   * Returns the document with the given id from the given collection mapped onto the given target class.
   *
   * @param id the id of the document to return
   * @param collectionName the collection to query for the document
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return object searched within the collection
   */
  <T> T findById(Object id, String collectionName);

  <T> T findOne(String jxQuery, Class<T> entityClass);
  <T> T findOne(String jxQuery, String collectionName);

  /**
   * Insert the object into correct collection. The collection type of the object is automatically determined.
   *
   * Insert is used to initially store the object into the database. To update an existing object use the save method.
   *
   * @param objectToSave the object to store in the collection
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void insert(Object objectToSave);

  /**
   * Insert the object into the specified collection.
   *
   * Insert is used to initially store the object into the database. To update an existing object use the save method.
   *
   * @param objectToSave the object to store in the collection
   * @param collectionName name of the collection to store the object in
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void insert(Object objectToSave, String collectionName);

  /**
   * Insert a Collection of objects into a collection in a single batch write to the database.
   *
   * @param batchToSave  the list of objects to save.
   * @param entityClass  class that determines the collection to use
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void insert(Collection<? extends T> batchToSave, Class<T> entityClass);

  /**
   * Insert a Collection of objects into a collection in a single batch write to the database.
   *
   * @param batchToSave  the list of objects to save.
   * @param collectionName  name of the collection to store the object in
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void insert(Collection<? extends T> batchToSave, String collectionName);

  /**
   * Save the object to the collection for the entity type of the object to save.
   * This will throw a exception if the object is not already present.
   * This is a not same as MongoDB behaviour
   *
   * @param objectToSave  the object to store in the collection
   * @param entityClass  class that determines the collection to use
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void save(Object objectToSave, Class<T> entityClass);

  /**
   * Save the object to the collection for the entity type of the object to save.
   * This will throw a exception if the object is not already present.
   * This is a not same as MongoDB behaviour
   *
   * @param objectToSave  the object to store in the collection
   * @param collectionName  name of the collection to store the object in
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void save(Object objectToSave, String collectionName);

  /**
   * Remove the given object from the collection by id.
   *
   * @param objectToRemove  the object to remove from the collection
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return  the object that was actually removed or null
   */
  <T> T remove(Object objectToRemove);

  /**
   * Remove the given object from the collection by id.
   *
   * @param objectToRemove  the object to remove from the collection
   * @param entityClass  class that determines the collection to use
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return  the object that was actually removed or null
   */
  <T> T remove(Object objectToRemove, Class<T> entityClass);

  /**
   * Remove the given object from the collection by id.
   *
   * @param objectToRemove  the object to remove from the collection
   * @param collectionName  name of the collection to remove the object from
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return  the object that was actually removed or null
   */
  <T> T remove(Object objectToRemove, String collectionName);

  /**
   * Remove a Collection of objects from a collection in a single batch write to the database.
   *
   * @param batchToRemove  the list of objects to remove.
   * @param entityClass  class that determines the collection to use
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return  List of objects actually removed or null
   */
  <T> List<T> remove(Collection<? extends T> batchToRemove, Class<T> entityClass);

  /**
   * Remove a Collection of objects from a collection in a single batch write to the database.
   *
   * @param batchToRemove  the list of objects to remove.
   * @param collectionName  name of the collection to remove the objects from
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return  List of objects actually removed or null
   */
  <T> List<T> remove(Collection<? extends T> batchToRemove, String collectionName);

  /**
   * Performs an upsert. If no document is found that matches the query, a new document is
   * created and inserted, else the found document is updated with contents of object
   *
   * @param objectToSave  the object to update.
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void upsert(Object objectToSave);

  /**
   * Performs an upsert. If no document is found that matches the query, a new document is
   * created and inserted, else the found document is updated with contents of object
   *
   * @param objectToSave  the object to update.
   * @param collectionName  name of the collection to update the objects from
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void upsert(Object objectToSave, String collectionName);

  /**
   * Performs an upsert. If no document is found that matches the input, new documents are
   * created and inserted, else the found document is updated with contents of object
   *
   * @param batchToSave  the list of objects to update.
   * @param entityClass  class that determines the collection to use
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void upsert(Collection<? extends T> batchToSave, Class<T> entityClass);

  /**
   * Performs an upsert. If no document is found that matches the input, new documents are
   * created and inserted, else the found document is updated with contents of object
   *
   * @param batchToSave  the list of objects to update.
   * @param collectionName  name of the collection to update the objects from
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void upsert(Collection<? extends T> batchToSave, String collectionName);

  /**
   * Map the results of the jxQuery on the collection for the entity type to a single
   * instance of an object of the specified type. The first document that matches the query
   * is returned and also removed from the collection in the database.
   *
   * Both the find and remove operation is done atomically
   *
   * @param jxQuery  JxPath query string
   * @param entityClass  class that determines the collection to use
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the removed object or null
   */
  <T> T findAndRemove(String jxQuery, Class<T> entityClass);

  /**
   * Map the results of the jxQuery on the collection for the entity type to a single
   * instance of an object of the specified type. The first document that matches the query
   * is returned and also removed from the collection in the database.
   *
   * Both the find and remove operation is done atomically
   *
   * @param jxQuery  JxPath query string
   * @param collectionName  name of the collection to update the objects from
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the removed object or null
   */
  <T> T findAndRemove(String jxQuery, String collectionName);

  /**
   * Returns and removes all documents matching the given query form the collection used to store the entityClass.
   *
   * Both the find and remove operation is done atomically
   *
   * @param jxQuery  JxPath query string
   * @param entityClass  class that determines the collection to use
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the list of removed objects or null
   */
  <T> List<T> findAllAndRemove(String jxQuery, Class<T> entityClass);

  /**
   * Returns and removes all documents matching the given query form the collection used to store the entityClass.
   *
   * Both the find and remove operation is done atomically
   *
   * @param jxQuery  JxPath query string
   * @param collectionName  name of the collection to update the objects from
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return the list of removed objects or null
   */
  <T> List<T> findAllAndRemove(String jxQuery, String collectionName);

  /**
   * Triggers findAndModify to apply provided Update on the first document matching Criteria of given Query.
   *
   * Both the find and remove operation is done atomically
   *
   * @param jxQuery  JxPath query string
   * @param update  The Update operation to perform
   * @param entityClass  class that determines the collection to use
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return first object that was modified or null
   */
  <T> T findAndModify(String jxQuery, Update update, Class<T> entityClass);

  /**
   * Triggers findAndModify to apply provided Update on the first document matching Criteria of given Query.
   *
   * Both the find and remove operation is done atomically
   *
   * @param jxQuery  JxPath query string
   * @param update  The Update operation to perform
   * @param collectionName  name of the collection to update the objects from
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   * @return first object that was modified or null
   */
  <T> T findAndModify(String jxQuery, Update update, String collectionName);

  <T> List<T> findAllAndModify(String jxQuery, Update update, Class<T> entityClass);
  <T> List<T> findAllAndModify(String jxQuery, Update update, String collectionName);

  /**
   * A method that allows changing the encryption algorithm and or encryption key used.
   *
   * This operation could take time. If for some reason the operation crashes in between
   * the database will be left in a inconsistent state, So it would be better to back up
   * the database before you carry out this operation
   * It will change all the json files that have any keys that are secret
   *
   * @param newCipher a new cipher to use, the algorithm may be same and just the key may be new
   * @param <T> Type annotated with {@link io.jsondb.annotation.Document} annotation
   *            and member of the baseScanPackage
   */
  <T> void changeEncryption(ICipher newCipher);

  /**
   * This method backs up JSONDB collections to specified backup path
   *
   * @param backupPath location at which to backup the database contents
   */
  void backup(String backupPath);

  /**
   * This method restores JSONDB collections from specified restore path.
   * if merge flag is set to true restore operation will merge collections from restore location
   * and if it is set to false it will replace existing collections with collections being
   * restored
   *
   * @param restorePath path were backup jsondb files are present
   * @param merge whether to merge data from restore location
   */
  void restore(String restorePath, boolean merge);
}
